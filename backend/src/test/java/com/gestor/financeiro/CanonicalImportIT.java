package com.gestor.financeiro;

import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportOrigin;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.importacao.ImportBatchService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
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

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres-it")
class CanonicalImportIT {
    private static final String HASH = "c".repeat(64);
    private static PostgreSQLContainer<?> postgres;

    @Autowired ImportBatchService service;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Usuario usuario;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("POSTGRES_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> env("POSTGRES_IT_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> env("POSTGRES_IT_PASSWORD", "postgres"));
            return;
        }
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("gestor_financeiro_import_it")
                .withUsername("postgres").withPassword("postgres");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void setup() {
        jdbcTemplate.update("delete from import_records");
        jdbcTemplate.update("delete from import_batches");
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Canonical Import", "canonical-" + System.nanoTime() + "@test.local", "hash"));
    }

    @Test
    void migrationCreatesConstraintsIndexesAndCascade() {
        ImportBatch batch = service.create(usuario.getId(), ImportFormat.CSV, null, HASH, "migration:1", ImportOrigin.UPLOAD);
        jdbcTemplate.update("""
                insert into import_records(batch_id, source_line, record_fingerprint, status, version)
                values (?, 1, ?, 'VALID', 0)
                """, batch.getId(), "d".repeat(64));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into import_records(batch_id, source_line, record_fingerprint, status, version)
                values (?, 0, ?, 'VALID', 0)
                """, batch.getId(), "e".repeat(64)));
        assertTrue(jdbcTemplate.queryForObject("""
                select count(*) > 0 from pg_indexes
                where tablename = 'import_batches' and indexname = 'ux_import_batches_user_idempotency'
                """, Boolean.class));

        jdbcTemplate.update("delete from import_batches where id = ?", batch.getId());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from import_records where batch_id = ?", Integer.class, batch.getId()));
    }

    @Test
    void concurrentRetryReturnsSingleBatch() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.create(
                    usuario.getId(), ImportFormat.OFX, "001", HASH, "concurrent:1", ImportOrigin.UPLOAD).getId());
            var second = executor.submit(() -> service.create(
                    usuario.getId(), ImportFormat.OFX, "001", HASH, "concurrent:1", ImportOrigin.UPLOAD).getId());
            assertEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*) from import_batches where usuario_id = ? and idempotency_key = 'concurrent:1'
                """, Integer.class, usuario.getId()));
    }

    @Test
    void optimisticVersionRejectsStaleLifecycleWrite() {
        ImportBatch batch = service.create(usuario.getId(), ImportFormat.CSV, null, HASH, null, ImportOrigin.UPLOAD);
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager stale = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            ImportBatch current = first.find(ImportBatch.class, batch.getId());
            ImportBatch outdated = stale.find(ImportBatch.class, batch.getId());
            current.setStatus(ImportBatchStatus.PARSED);
            first.getTransaction().commit();
            outdated.setStatus(ImportBatchStatus.FAILED);
            outdated.setFailureCode("STALE_WRITE");
            assertThrows(RuntimeException.class, () -> stale.getTransaction().commit());
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            first.close();
            stale.close();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
