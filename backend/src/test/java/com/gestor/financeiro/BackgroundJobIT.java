package com.gestor.financeiro;

import com.gestor.financeiro.exception.FinancialConflictException;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.BackgroundJobService;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("postgres-it")
class BackgroundJobIT {

    private static PostgreSQLContainer<?> postgres;

    /**
     * O claim compara available_at com o relógio do banco; o teste enfileira com folga para não
     * depender de o relógio do host estar alinhado ao do container.
     */
    private static Instant disponivelAgora() { return Instant.now().minusSeconds(5); }

    @Autowired BackgroundJobService service;
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
    void cleanJobs() {
        jdbcTemplate.update("delete from background_jobs");
    }

    @Test
    void enqueueEhIdempotenteMasRejeitaConteudoDiferente() {
        long first = service.enqueue("it:idem", "IMPORT_PARSE", "{\"batchId\":1}", (short) 1,
                0, Instant.now(), 3);
        long repeated = service.enqueue("it:idem", "IMPORT_PARSE", "{\"batchId\":1}", (short) 1,
                0, Instant.now(), 3);

        assertEquals(first, repeated);
        assertThrows(FinancialConflictException.class, () -> service.enqueue(
                "it:idem", "IMPORT_PARSE", "{\"batchId\":2}", (short) 1, 0, Instant.now(), 3));
    }

    @Test
    void workersRecebemJobsDistintosELeaseProtegeConclusao() {
        service.enqueue("it:claim:1", "IMPORT_PARSE", "{}", (short) 1, 0, disponivelAgora(), 3);
        service.enqueue("it:claim:2", "IMPORT_PARSE", "{}", (short) 1, 0, disponivelAgora(), 3);

        List<BackgroundJob> first = service.claim("worker-a", 1, Duration.ofMinutes(1));
        List<BackgroundJob> second = service.claim("worker-b", 1, Duration.ofMinutes(1));

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertTrue(first.get(0).id() != second.get(0).id());
        assertTrue(service.complete(first.get(0).id(), "worker-a"));
        assertTrue(!service.complete(second.get(0).id(), "worker-a"));
    }

    @Test
    void ultimaFalhaVaiParaDeadLetter() {
        service.enqueue("it:dead", "IMPORT_PARSE", "{}", (short) 1, 0, disponivelAgora(), 1);
        BackgroundJob job = service.claim("worker-dead", 1, Duration.ofMinutes(1)).get(0);

        assertTrue(service.fail(job.id(), "worker-dead", "IMPORT_PARSE_FAILED", Duration.ZERO));
        assertEquals("DEAD_LETTER", jdbcTemplate.queryForObject(
                "select status from background_jobs where id = ?", String.class, job.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from background_jobs where id = ? and lease_owner is null and finished_at is not null",
                Integer.class, job.id()));
    }

    @Test
    void claimConcorrenteNaoEntregaOMesmoJobDuasVezes() throws Exception {
        int jobs = 8, workers = 4;
        for (int i = 0; i < jobs; i++) {
            service.enqueue("it:race:" + i, "IMPORT_PARSE", "{}", (short) 1, 0, disponivelAgora(), 3);
        }

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch largada = new CountDownLatch(1);
        ConcurrentLinkedQueue<Long> reivindicados = new ConcurrentLinkedQueue<>();
        List<Future<Integer>> resultados = new ArrayList<>();
        try {
            for (int w = 0; w < workers; w++) {
                String worker = "worker-race-" + w;
                Callable<Integer> tarefa = () -> {
                    largada.await();
                    List<BackgroundJob> lote = service.claim(worker, 2, Duration.ofMinutes(1));
                    lote.forEach(job -> reivindicados.add(job.id()));
                    return lote.size();
                };
                resultados.add(pool.submit(tarefa));
            }
            largada.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "possível deadlock no claim concorrente");
            for (Future<Integer> resultado : resultados) resultado.get();
        } finally {
            pool.shutdownNow();
        }

        List<Long> todos = new ArrayList<>(reivindicados);
        Set<Long> distintos = new HashSet<>(todos);
        assertEquals(todos.size(), distintos.size(), "mesmo job entregue a dois workers");
        assertEquals(jobs, distintos.size(), "todos os jobs disponíveis deveriam ser reivindicados");
    }

    @Test
    void leaseExpiradoPermiteReivindicacaoPorOutroWorker() {
        service.enqueue("it:lease", "IMPORT_PARSE", "{}", (short) 1, 0, disponivelAgora(), 3);

        // Lease abaixo de um segundo nasce vencido: é o cenário de worker morto sem renovar.
        BackgroundJob primeiro = service.claim("worker-morto", 1, Duration.ofMillis(500)).get(0);
        List<BackgroundJob> retomada = service.claim("worker-vivo", 1, Duration.ofMinutes(1));

        assertEquals(1, retomada.size());
        assertEquals(primeiro.id(), retomada.get(0).id());
        assertEquals(2, retomada.get(0).attempts());
        assertFalse(service.complete(primeiro.id(), "worker-morto"), "worker sem lease não pode concluir");
        assertTrue(service.complete(primeiro.id(), "worker-vivo"));
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
