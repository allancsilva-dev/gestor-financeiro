package com.gestor.financeiro;

import com.gestor.financeiro.dto.OrcamentoCategoriaRequest;
import com.gestor.financeiro.dto.OrcamentoRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.OrcamentoService;
import com.gestor.financeiro.service.job.BackgroundJobService;
import com.gestor.financeiro.service.job.BackgroundJobWorker;
import com.gestor.financeiro.service.job.JobHandler;
import com.gestor.financeiro.service.orcamento.OrcamentoFechamentoScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fechamento de orçamento contra PostgreSQL real: a fila fecha a competência e o banco recusa
 * fechamento que não fecha a conta — nenhum dos dois é verificável em H2.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class OrcamentoRolloverIT {

    private static PostgreSQLContainer<?> postgres;

    @Autowired OrcamentoFechamentoScheduler scheduler;
    @Autowired OrcamentoService orcamentoService;
    @Autowired BackgroundJobService jobs;
    @Autowired List<JobHandler> handlers;
    @Autowired MeterRegistry meterRegistry;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired JdbcTemplate jdbcTemplate;

    private Usuario usuario;
    private Categoria categoria;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("POSTGRES_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> getenvOrDefault("POSTGRES_IT_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> getenvOrDefault("POSTGRES_IT_PASSWORD", "postgres"));
            return;
        }
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("gestor_financeiro_it")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterAll
    static void stopPostgresContainer() {
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void setup() {
        jdbcTemplate.update("delete from background_jobs");
        jdbcTemplate.update("delete from orcamento_fechamentos");
        jdbcTemplate.update("delete from orcamentos_categorias");
        jdbcTemplate.update("delete from orcamentos_mensais");
        jdbcTemplate.update("delete from transacoes");
        jdbcTemplate.update("delete from categorias");
        jdbcTemplate.update("delete from usuarios");
        usuario = usuarios.save(TestDataFactory.usuario("Rollover", "rollover-it@test.local", "hash"));
        categoria = categorias.save(TestDataFactory.categoria(usuario, "Mercado"));
    }

    /** O agendador enfileira para todo titular do banco; aqui só interessa o meu. */
    private String chaveDoTitular() {
        return "BUDGET_CLOSE:" + usuario.getId() + ":" + competenciaAnterior();
    }

    private int jobsDoTitular() {
        return jdbcTemplate.queryForObject("select count(*) from background_jobs where job_key = ?",
                Integer.class, chaveDoTitular());
    }

    /**
     * Drena até a fila esvaziar: outro teste da suíte pode ter deixado titulares no banco, e uma
     * rodada só pegaria o job de outro usuário.
     */
    private void drenarFila() {
        BackgroundJobWorker worker = new BackgroundJobWorker(jobs, meterRegistry, handlers, true, 1, 1, 30, 60);
        for (int rodada = 0; rodada < 50 && worker.executarUmaRodada("worker-orcamento-it"); rodada++) {
            // segue drenando
        }
    }

    private YearMonth competenciaAnterior() {
        return YearMonth.from(LocalDate.now()).minusMonths(1);
    }

    private void limite(YearMonth competencia, String valor, String politica) {
        OrcamentoCategoriaRequest linha = new OrcamentoCategoriaRequest();
        linha.setCategoriaId(categoria.getId());
        linha.setValorLimite(new BigDecimal(valor));
        linha.setPoliticaRollover(politica);

        OrcamentoRequest request = new OrcamentoRequest();
        request.setMes(competencia.getMonthValue());
        request.setAno(competencia.getYear());
        request.setCategorias(List.of(linha));
        orcamentoService.criarOuAtualizar(usuario.getId(), request);
    }

    @Test
    void filaFechaACompetenciaAnteriorUmaVezSo() {
        YearMonth anterior = competenciaAnterior();
        limite(anterior, "800.00", "SURPLUS_ONLY");

        scheduler.enfileirarCompetenciaAnterior();
        // Cron roda todo dia: enfileirar de novo não pode gerar um segundo job da mesma competência.
        scheduler.enfileirarCompetenciaAnterior();
        assertEquals(1, jobsDoTitular(), "job_key determinística por titular e competência");

        drenarFila();

        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "select status from background_jobs where job_key = ?", String.class, chaveDoTitular()));
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "select count(*) from orcamento_fechamentos where usuario_id = ?", Integer.class, usuario.getId()));
        assertEquals(0, new BigDecimal("800.00").compareTo(jdbcTemplate.queryForObject(
                "select carry_out from orcamento_fechamentos where usuario_id = ?",
                BigDecimal.class, usuario.getId())));
    }

    @Test
    void bancoRecusaFechamentoQueNaoFechaAConta() {
        YearMonth anterior = competenciaAnterior();
        limite(anterior, "800.00", "BOTH");
        scheduler.enfileirarCompetenciaAnterior();
        drenarFila();
        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "select count(*) from orcamento_fechamentos where usuario_id = ?", Integer.class, usuario.getId()));

        // resultado precisa ser base + carry_in - gasto; qualquer outro número é conta errada.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "update orcamento_fechamentos set resultado = resultado + 1 where usuario_id = ?",
                usuario.getId()));

        // e o que passa adiante não pode contrariar a política registrada.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "update orcamento_fechamentos set politica = 'NONE' where usuario_id = ?",
                usuario.getId()));
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
