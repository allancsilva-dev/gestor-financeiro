package com.gestor.financeiro;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.NotificacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.job.BackgroundJobService;
import com.gestor.financeiro.service.job.BackgroundJobWorker;
import com.gestor.financeiro.service.job.JobHandler;
import com.gestor.financeiro.service.notificacao.NotificacaoAgendamentoService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sincronização diária: o cron enfileira, o worker executa.
 *
 * <p>Antes disso, notificação só nascia quando alguém abria a home — quem não abria o app não era
 * avisado de fatura vencendo, que é justamente quando o aviso serve.</p>
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class NotificacaoAgendadaIT {

    private static PostgreSQLContainer<?> postgres;

    @Autowired NotificacaoAgendamentoService agendamento;
    @Autowired BackgroundJobService jobs;
    @Autowired BackgroundJobWorker workerBean;
    @Autowired List<JobHandler> handlers;
    @Autowired MeterRegistry meterRegistry;
    @Autowired UsuarioRepository usuarios;
    @Autowired NotificacaoRepository notificacoes;
    @Autowired JdbcTemplate jdbcTemplate;

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
    void limpar() {
        jdbcTemplate.update("delete from background_jobs");
        jdbcTemplate.update("delete from notificacoes");
        jdbcTemplate.update("delete from notificacao_dispositivos");
        jdbcTemplate.update("delete from usuarios");
    }

    private Usuario titular(String sufixo) {
        return usuarios.save(TestDataFactory.usuario("Agendada", "agendada-" + sufixo + "@test.local", "hash"));
    }

    @Test
    void enfileiraUmJobPorTitularSemRepetirNoMesmoDia() {
        titular("a");
        titular("b");

        assertEquals(2, agendamento.enfileirarDoDia());
        // Cron rodando duas vezes, ou duas instâncias: a job_key determinística resolve no banco.
        assertEquals(2, agendamento.enfileirarDoDia());

        assertEquals(2, (int) jdbcTemplate.queryForObject(
                "select count(*) from background_jobs where job_type = 'NOTIFICATION_SYNC'", Integer.class));
    }

    @Test
    void workerExecutaASincronizacaoDoTitular() {
        Usuario dono = titular("worker");
        agendamento.enfileirarDoDia();

        BackgroundJobWorker worker = new BackgroundJobWorker(jobs, meterRegistry, handlers, true, 1, 1, 30, 60);
        assertTrue(worker.executarUmaRodada("worker-notificacao-it"));

        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "select status from background_jobs where job_type = 'NOTIFICATION_SYNC'", String.class));
        // Sem eventos, a sincronização não inventa aviso — mas roda sem erro para o titular.
        assertEquals(0, notificacoes.countByUsuarioIdAndLidaFalse(dono.getId()));
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
