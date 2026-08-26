package com.gestor.financeiro;

import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.BackgroundJobService;
import com.gestor.financeiro.service.job.BackgroundJobWorker;
import com.gestor.financeiro.service.job.JobHandler;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumo da fila durável: conclusão, retentativa, dead letter, tipo sem handler e o laço de
 * segundo plano com parada limpa. O worker é construído no teste para o consumo ser determinístico.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class BackgroundJobWorkerIT {

    private static PostgreSQLContainer<?> postgres;

    @Autowired BackgroundJobService jobs;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MeterRegistry meterRegistry;

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
    void limparFila() {
        jdbcTemplate.update("delete from background_jobs");
    }

    private BackgroundJobWorker worker(List<JobHandler> handlers) {
        return new BackgroundJobWorker(jobs, meterRegistry, handlers, true, 1, 1, 30, 60);
    }

    private String status(long jobId) {
        return jdbcTemplate.queryForObject("select status from background_jobs where id = ?", String.class, jobId);
    }

    private Instant agora() {
        return Instant.now().minusSeconds(5);
    }

    @Test
    void jobBemSucedidoEhConcluidoUmaVez() {
        HandlerQueContabiliza handler = new HandlerQueContabiliza("IMPORT_PARSE");
        long id = jobs.enqueue("worker:ok", "IMPORT_PARSE", "{\"batchId\":7}", (short) 1, 0, agora(), 3);

        assertTrue(worker(List.of(handler)).executarUmaRodada("worker-it-ok"));

        assertEquals("COMPLETED", status(id));
        assertEquals(1, handler.recebidos.size());
        assertEquals("{\"batchId\": 7}", handler.recebidos.get(0).payload());
    }

    @Test
    void falhaDoHandlerViraRetryEDepoisDeadLetter() {
        JobHandler explode = new JobHandler() {
            @Override public String type() { return "IMPORT_COMMIT"; }
            @Override public void handle(BackgroundJob job) { throw new IllegalStateException("falha simulada"); }
        };
        long id = jobs.enqueue("worker:falha", "IMPORT_COMMIT", "{}", (short) 1, 0, agora(), 2);
        BackgroundJobWorker worker = worker(List.of(explode));

        worker.executarUmaRodada("worker-it-falha");
        assertEquals("RETRY", status(id));

        // Backoff empurra available_at para frente; o teste antecipa para exercitar a 2ª tentativa.
        jdbcTemplate.update("update background_jobs set available_at = current_timestamp - interval '1 second' where id = ?", id);
        worker.executarUmaRodada("worker-it-falha");

        assertEquals("DEAD_LETTER", status(id));
        assertEquals("IMPORT_COMMIT_FAILED", jdbcTemplate.queryForObject(
                "select last_error from background_jobs where id = ?", String.class, id));
    }

    @Test
    void tipoSemHandlerNaoFicaGirandoNaFila() {
        long id = jobs.enqueue("worker:orfao", "TIPO_INEXISTENTE", "{}", (short) 1, 0, agora(), 1);

        assertTrue(worker(List.of()).executarUmaRodada("worker-it-orfao"));

        assertEquals("DEAD_LETTER", status(id));
        assertEquals("NO_HANDLER", jdbcTemplate.queryForObject(
                "select last_error from background_jobs where id = ?", String.class, id));
    }

    @Test
    void filaVaziaNaoReportaTrabalho() {
        assertFalse(worker(List.of()).executarUmaRodada("worker-it-vazio"));
    }

    @Test
    void lacoDeSegundoPlanoConsomeEParaLimpo() throws Exception {
        CountDownLatch processado = new CountDownLatch(1);
        JobHandler handler = new JobHandler() {
            @Override public String type() { return "NOTIFICATION_SYNC"; }
            @Override public void handle(BackgroundJob job) { processado.countDown(); }
        };
        long id = jobs.enqueue("worker:laco", "NOTIFICATION_SYNC", "{}", (short) 1, 0, agora(), 3);

        BackgroundJobWorker worker = worker(List.of(handler));
        worker.start();
        try {
            assertTrue(processado.await(30, TimeUnit.SECONDS), "worker não consumiu o job");
            long limite = System.currentTimeMillis() + 10_000;
            while (!"COMPLETED".equals(status(id)) && System.currentTimeMillis() < limite) {
                Thread.sleep(100);
            }
            assertEquals("COMPLETED", status(id));
        } finally {
            worker.stop();
        }
        assertFalse(worker.isRunning());
    }

    private static final class HandlerQueContabiliza implements JobHandler {
        private final String tipo;
        private final List<BackgroundJob> recebidos = new CopyOnWriteArrayList<>();

        private HandlerQueContabiliza(String tipo) {
            this.tipo = tipo;
        }

        @Override public String type() { return tipo; }

        @Override public void handle(BackgroundJob job) {
            recebidos.add(job);
        }
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
