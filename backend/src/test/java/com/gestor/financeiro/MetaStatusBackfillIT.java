package com.gestor.financeiro;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Valida V30 e a correção V31 contra PostgreSQL real.
 */
class MetaStatusBackfillIT {

    private static PostgreSQLContainer<?> postgres;
    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void start() {
        String externalUrl = System.getenv("POSTGRES_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            url = externalUrl;
            user = getenvOrDefault("POSTGRES_IT_USERNAME", "postgres");
            password = getenvOrDefault("POSTGRES_IT_PASSWORD", "postgres");
        } else {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gestor_financeiro_backfill_it")
                    .withUsername("postgres")
                    .withPassword("postgres");
            postgres.start();
            url = postgres.getJdbcUrl();
            user = postgres.getUsername();
            password = postgres.getPassword();
        }
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    @Test
    void backfillClassificaMetasLegadasNosTresEstados() throws SQLException {
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("29")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO usuarios (nome, email, senha) VALUES ('Legado', 'legado@teste.com', 'hash')");
            st.execute("""
                    INSERT INTO metas (usuario_id, nome, valor_total, valor_reservado, ativa, data_conclusao) VALUES
                    (1, 'ativa',      1000, 100, TRUE,  NULL),
                    (1, 'concluida',  1000, 1000, FALSE, '2026-06-30'),
                    (1, 'arquivada',  1000, 0,   FALSE, NULL),
                    (1, 'ativa_nula', 1000, 0,   NULL,  NULL)
                    """);
        }

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("30")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement st = conn.createStatement()) {
            assertEquals("ATIVA", statusDe(st, "ativa"));
            assertEquals("CONCLUIDA", statusDe(st, "concluida"));
            assertEquals("ARQUIVADA", statusDe(st, "arquivada"));
            assertEquals("ATIVA", statusDe(st, "ativa_nula"));

            // Simula escrita inconsistente ocorrida depois da V30.
            st.execute("UPDATE metas SET ativa = FALSE WHERE nome = 'ativa'");
            st.execute("UPDATE metas SET ativa = TRUE WHERE nome = 'concluida'");
        }

        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement st = conn.createStatement()) {
            assertEquals(true, ativaDe(st, "ativa"));
            assertEquals(false, ativaDe(st, "concluida"));
            assertEquals(true, ativaDe(st, "ativa_nula"));

            // V31 impede nova divergência entre os dois campos.
            assertThrows(SQLException.class, () -> st.execute(
                    "UPDATE metas SET ativa = TRUE WHERE nome = 'arquivada'"));
            assertThrows(SQLException.class, () -> st.execute(
                    "INSERT INTO metas (usuario_id, nome, valor_total, status, ativa) VALUES (1, 'invalida', 10, 'PAUSADA', FALSE)"));
        }
    }

    private String statusDe(Statement st, String nome) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT status FROM metas WHERE nome = '" + nome + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }


    private boolean ativaDe(Statement st, String nome) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT ativa FROM metas WHERE nome = '" + nome + "'")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }
}
