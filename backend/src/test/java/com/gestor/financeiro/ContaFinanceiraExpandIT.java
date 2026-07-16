package com.gestor.financeiro;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-02 — V32: expand de carteiras para conta financeira (ADR-0008).
 * Comprova em PostgreSQL real: defaults do expand, dominio dos CHECKs,
 * coerencia natureza/subtipo e saldo tecnico zero de CUSTODIA.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class ContaFinanceiraExpandIT {

    private static PostgreSQLContainer<?> postgres;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("POSTGRES_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> getenvOrDefault("POSTGRES_IT_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> getenvOrDefault("POSTGRES_IT_PASSWORD", "postgres"));
            return;
        }

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Long novoUsuario(String email) {
        return jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('F2', ?, 'x', 0, false) returning id",
                Long.class, email);
    }

    @Test
    void expandAplicaDefaultsSemTocarSaldo() {
        Long usuarioId = novoUsuario("expand-defaults@teste.com");
        Long id = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Legada', 'CORRENTE', 123.45, ?, 0) returning id",
                Long.class, usuarioId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select natureza, subtipo, liquidez, origem_dados, estado_conciliacao, moeda, saldo from carteiras where id = ?",
                id);
        assertEquals("ATIVO", row.get("natureza"));
        assertEquals("CORRENTE", row.get("subtipo"));
        assertEquals("IMEDIATA", row.get("liquidez"));
        assertEquals("MANUAL", row.get("origem_dados"));
        assertEquals("CONCILIADA", row.get("estado_conciliacao"));
        assertEquals("BRL", row.get("moeda"));
        assertEquals(0, new java.math.BigDecimal("123.45").compareTo((java.math.BigDecimal) row.get("saldo")));
    }

    @Test
    void checksRejeitamCombinacoesInvalidas() {
        Long usuarioId = novoUsuario("expand-checks@teste.com");

        // CUSTODIA com saldo monetario e proibido (ck_carteiras_custodia_saldo_zero)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Corretora', 'CUSTODIA', 10.00, ?, 0)",
                usuarioId));

        // CARTAO deve ser PASSIVO (ck_carteiras_natureza_subtipo)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, natureza, saldo, usuario_id, version) values ('Cartao', 'CARTAO', 'ATIVO', 0, ?, 0)",
                usuarioId));

        // PASSIVO so existe como CARTAO nesta fase
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, natureza, saldo, usuario_id, version) values ('Divida', 'CORRENTE', 'PASSIVO', 0, ?, 0)",
                usuarioId));

        // Moeda unica BRL nesta fase (ck_carteiras_moeda)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, moeda, saldo, usuario_id, version) values ('Dolar', 'CORRENTE', 'USD', 0, ?, 0)",
                usuarioId));

        // Subtipo fora do dominio rejeitado (ck_carteiras_subtipo)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Foo', 'FOO', 0, ?, 0)",
                usuarioId));

        // Combinacoes validas continuam passando: CUSTODIA saldo 0 e CARTAO passivo
        assertEquals(1, jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Corretora', 'CUSTODIA', 0, ?, 0)",
                usuarioId));
        assertEquals(1, jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, natureza, saldo, usuario_id, version) values ('Cartao', 'CARTAO', 'PASSIVO', 0, ?, 0)",
                usuarioId));
        assertEquals(1, jdbcTemplate.update(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Cofre', 'COFRE', 0, ?, 0)",
                usuarioId));
    }
}
