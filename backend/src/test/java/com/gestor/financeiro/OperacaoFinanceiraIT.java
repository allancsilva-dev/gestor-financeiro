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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-03 — V33 em PostgreSQL real: unicidade de idempotency por usuario na
 * OPERACAO, dominio dos CHECKs, FK de estorno e colunas operacao_id aditivas.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class OperacaoFinanceiraIT {

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
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('F2Op', ?, 'x', 0, false) returning id",
                Long.class, email);
    }

    @Test
    void idempotencyKeyEUnicaPorUsuarioNaOperacao() {
        Long u1 = novoUsuario("op-idem-a@teste.com");
        Long u2 = novoUsuario("op-idem-b@teste.com");

        jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao, idempotency_key) values (?, 'TRANSFERENCIA', current_timestamp, 'chave-1')",
                u1);

        // mesma chave, mesmo usuario: rejeitada pelo indice parcial
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao, idempotency_key) values (?, 'TRANSFERENCIA', current_timestamp, 'chave-1')",
                u1));

        // mesma chave, outro usuario: permitida
        assertEquals(1, jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao, idempotency_key) values (?, 'TRANSFERENCIA', current_timestamp, 'chave-1')",
                u2));

        // duas operacoes sem chave para o mesmo usuario: permitidas
        assertEquals(1, jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao) values (?, 'AJUSTE', current_timestamp)", u1));
        assertEquals(1, jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao) values (?, 'AJUSTE', current_timestamp)", u1));
    }

    @Test
    void checksRejeitamDominioInvalidoEEstornoSemReferencia() {
        Long usuarioId = novoUsuario("op-checks@teste.com");

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao) values (?, 'FOO', current_timestamp)",
                usuarioId));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, status, data_operacao) values (?, 'AJUSTE', 'CANCELADA', current_timestamp)",
                usuarioId));

        // ESTORNO exige estorno_de_id (ck_operacoes_estorno_ref)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao) values (?, 'ESTORNO', current_timestamp)",
                usuarioId));

        Long originalId = jdbcTemplate.queryForObject(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao) values (?, 'TRANSACAO', current_timestamp) returning id",
                Long.class, usuarioId);
        assertEquals(1, jdbcTemplate.update(
                "insert into operacoes_financeiras(usuario_id, tipo, data_operacao, estorno_de_id) values (?, 'ESTORNO', current_timestamp, ?)",
                usuarioId, originalId));
    }

    @Test
    void colunasOperacaoIdExistemNasQuatroTabelasDeLancamento() {
        Integer colunas = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where column_name = 'operacao_id'
                  and table_name in ('movimentos_carteira', 'fatura_lancamentos',
                                     'movimentacoes_ativo', 'movimentos_meta')
                """, Integer.class);
        assertEquals(4, colunas);

        Integer pagamentos = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'fatura_pagamentos'",
                Integer.class);
        assertEquals(1, pagamentos);
    }
}
