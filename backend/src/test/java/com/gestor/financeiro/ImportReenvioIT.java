package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.importacao.CanonicalImportOrchestrator;
import com.gestor.financeiro.service.importacao.ImportCommitService;
import com.gestor.financeiro.service.importacao.ImportSource;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reenviar o mesmo arquivo depois de lançar: o caminho real do usuário que importa duas vezes.
 *
 * <p>Só o PostgreSQL tem o CHECK `ck_import_batches_counts`; em H2 um contador incoerente passaria
 * despercebido — foi exatamente assim que este defeito escapou até a verificação em runtime.</p>
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class ImportReenvioIT {

    private static PostgreSQLContainer<?> postgres;

    private static final String CSV = String.join("\n",
            "data,descricao,valor,moeda,tipo",
            "2026-08-20,Mercado Centro,-120.50,BRL,SAIDA",
            "2026-08-21,Salario,3000.00,BRL,ENTRADA",
            "2026-08-22,Farmacia,-45.90,BRL,SAIDA",
            "2026-08-23,Sem moeda,-10.00,,SAIDA") + "\n";

    @Autowired CanonicalImportOrchestrator orchestrator;
    @Autowired ImportCommitService commitService;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportRecordRepository records;
    @Autowired CarteiraRepository carteiras;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbcTemplate;

    private Usuario usuario;
    private Carteira conta;

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
        jdbcTemplate.update("delete from import_records");
        jdbcTemplate.update("delete from import_batches");
        jdbcTemplate.update("delete from movimentos_carteira");
        jdbcTemplate.update("delete from transacoes");
        usuario = usuarios.save(TestDataFactory.usuario("Reenvio",
                "reenvio-" + System.nanoTime() + "@test.local", "hash"));
        conta = carteiras.save(TestDataFactory.carteira(usuario, "Conta corrente", new BigDecimal("2500.00")));
    }

    private ImportBatch enviar(String idempotencyKey) throws Exception {
        return orchestrator.stage(usuario.getId(), new FonteEmMemoria(CSV), idempotencyKey);
    }

    @Test
    void reenvioDepoisDoLancamentoMarcaDuplicadosSemQuebrarContadores() throws Exception {
        ImportBatch primeiro = enviar("reenvio:1");
        assertEquals(3, primeiro.getValidRecords());
        assertEquals(1, primeiro.getPendingReviewRecords());

        commitService.preparar(usuario.getId(), primeiro.getId(), conta.getId());
        commitService.solicitarCommit(usuario.getId(), primeiro.getId());
        commitService.executar(usuario.getId(), primeiro.getId());
        assertEquals(3, (int) jdbcTemplate.queryForObject(
                "select count(*) from transacoes where usuario_id = ?", Integer.class, usuario.getId()));

        ImportBatch segundo = enviar("reenvio:2");

        assertEquals(ImportBatchStatus.PARSED, segundo.getStatus());
        assertEquals(4, segundo.getTotalRecords());
        assertEquals(3, segundo.getDuplicateRecords(), "as três linhas já lançadas são repetição");
        assertEquals(0, segundo.getValidRecords(), "linha repetida não pode continuar contada como pronta");
        assertEquals(1, segundo.getPendingReviewRecords());
        assertEquals(3, records.countByBatchIdAndStatus(segundo.getId(), ImportRecordStatus.DUPLICATE));
    }

    /** Fonte reabrível em memória; o orquestrador lê o conteúdo mais de uma vez. */
    private record FonteEmMemoria(String conteudo) implements ImportSource {
        @Override public InputStream openStream() {
            return new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.UTF_8));
        }
        @Override public long size() { return conteudo.getBytes(StandardCharsets.UTF_8).length; }
        @Override public String displayName() { return "extrato.csv"; }
        @Override public String contentType() { return "text/csv"; }
        @Override public String sha256() { return null; }
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
