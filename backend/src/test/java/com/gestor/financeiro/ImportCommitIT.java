package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.importacao.ImportCommitService;
import com.gestor.financeiro.service.job.BackgroundJobWorker;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Commit do lote contra PostgreSQL real: a fila entrega o trabalho, o ledger fecha a centavo,
 * reexecução não duplica e as constraints da V46/V49 recusam estado incoerente — nada disso é
 * verificável em H2.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class ImportCommitIT {

    private static PostgreSQLContainer<?> postgres;

    @Autowired ImportCommitService commitService;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportRecordRepository records;
    @Autowired CarteiraRepository carteiras;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired com.gestor.financeiro.service.job.BackgroundJobService jobs;
    @Autowired MeterRegistry meterRegistry;
    @Autowired List<com.gestor.financeiro.service.job.JobHandler> handlers;

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
        usuario = usuarios.save(TestDataFactory.usuario("CommitIT",
                "commit-it-" + System.nanoTime() + "@test.local", "hash"));
        conta = carteiras.save(TestDataFactory.carteira(usuario, "Conta corrente", new BigDecimal("500.00")));
    }

    private ImportBatch loteComDuasSaidas() {
        ImportBatch batch = new ImportBatch();
        batch.setUsuario(usuario);
        batch.setFormat(ImportFormat.CSV);
        batch.setFileSha256("a".repeat(64));
        batch.setStatus(ImportBatchStatus.PARSED);
        batch.setTotalRecords(2);
        batch.setValidRecords(2);
        ImportBatch salvo = batches.save(batch);
        for (int linha = 2; linha <= 3; linha++) {
            ImportRecord record = new ImportRecord();
            record.setBatch(salvo);
            record.setSourceLine(linha);
            record.setRecordFingerprint(String.format("%064x", linha));
            record.setStatus(ImportRecordStatus.VALID);
            record.setOccurredOn(LocalDate.of(2026, 8, 20));
            record.setNormalizedDescription("Compra " + linha);
            record.setAmount(new BigDecimal("25.00"));
            record.setCurrency("BRL");
            record.setDirection(TipoTransacao.SAIDA);
            records.save(record);
        }
        return salvo;
    }

    private BigDecimal saldo() {
        return carteiras.findById(conta.getId()).orElseThrow().getSaldo();
    }

    private BigDecimal somaDoLedger() {
        return jdbcTemplate.queryForObject(
                "select coalesce(sum(valor_assinado), 0) from movimentos_carteira where carteira_id = ?",
                BigDecimal.class, conta.getId());
    }

    @Test
    void filaLancaOLoteEOSaldoFechaComOLedger() {
        ImportBatch batch = loteComDuasSaidas();
        commitService.preparar(usuario.getId(), batch.getId(), conta.getId());
        commitService.solicitarCommit(usuario.getId(), batch.getId());

        assertEquals(1, (int) jdbcTemplate.queryForObject(
                "select count(*) from background_jobs where job_type = 'IMPORT_COMMIT'", Integer.class));

        BackgroundJobWorker worker = new BackgroundJobWorker(jobs, meterRegistry, handlers, true, 1, 1, 30, 60);
        assertTrue(worker.executarUmaRodada("worker-commit-it"));

        assertEquals(ImportBatchStatus.COMMITTED, batches.findById(batch.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("450.00").compareTo(saldo()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(somaDoLedger()));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "select status from background_jobs where job_type = 'IMPORT_COMMIT'", String.class));
    }

    @Test
    void reexecucaoDoJobNaoDuplicaLancamento() {
        ImportBatch batch = loteComDuasSaidas();
        commitService.preparar(usuario.getId(), batch.getId(), conta.getId());
        commitService.solicitarCommit(usuario.getId(), batch.getId());

        BackgroundJobWorker worker = new BackgroundJobWorker(jobs, meterRegistry, handlers, true, 1, 1, 30, 60);
        worker.executarUmaRodada("worker-commit-it");
        // Mesmo lote reprocessado: é o que acontece quando o lease vence com o job em andamento.
        commitService.executar(usuario.getId(), batch.getId());

        assertEquals(2, (int) jdbcTemplate.queryForObject(
                "select count(*) from transacoes where usuario_id = ?", Integer.class, usuario.getId()));
        assertEquals(0, new BigDecimal("450.00").compareTo(saldo()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(somaDoLedger()));
    }

    @Test
    void bancoRecusaLoteEmCommitSemContaDeDestino() {
        ImportBatch batch = loteComDuasSaidas();

        // Guard da V49: sem destino, o lote não pode nem entrar em COMMITTING.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "update import_batches set status = 'COMMITTING' where id = ?", batch.getId()));
    }

    @Test
    void bancoRecusaRegistroCommittedSemTransacao() {
        ImportBatch batch = loteComDuasSaidas();
        Long registroId = records.findAll().get(0).getId();

        // Guard da V46: COMMITTED sem vínculo de transação é estado impossível.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "update import_records set status = 'COMMITTED' where id = ?", registroId));
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
