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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("postgres-it")
class BackgroundJobIT {

    private static PostgreSQLContainer<?> postgres;

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
        service.enqueue("it:claim:1", "IMPORT_PARSE", "{}", (short) 1, 0, Instant.now(), 3);
        service.enqueue("it:claim:2", "IMPORT_PARSE", "{}", (short) 1, 0, Instant.now(), 3);

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
        service.enqueue("it:dead", "IMPORT_PARSE", "{}", (short) 1, 0, Instant.now(), 1);
        BackgroundJob job = service.claim("worker-dead", 1, Duration.ofMinutes(1)).get(0);

        assertTrue(service.fail(job.id(), "worker-dead", "IMPORT_PARSE_FAILED", Duration.ZERO));
        assertEquals("DEAD_LETTER", jdbcTemplate.queryForObject(
                "select status from background_jobs where id = ?", String.class, job.id()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from background_jobs where id = ? and lease_owner is null and finished_at is not null",
                Integer.class, job.id()));
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
