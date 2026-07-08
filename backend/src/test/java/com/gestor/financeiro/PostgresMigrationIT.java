package com.gestor.financeiro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("postgres-it")
class PostgresMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gestor_financeiro_it")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void flywayAplicaMigrationsEmPostgresLimpoEHibernateValidaSchema() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);

        assertNotNull(appliedMigrations);
        assertTrue(appliedMigrations >= 12);
    }

    @Test
    void migrationCriaMovimentosCarteiraComConstraintsPrincipais() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'movimentos_carteira'",
                Integer.class);
        assertNotNull(tableCount);
        assertTrue(tableCount > 0);

        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Ledger', 'ledger-it@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long carteiraId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, tipo, saldo, usuario_id, version) values ('Principal', 'DINHEIRO', 100.00, ?, 0) returning id",
                Long.class,
                usuarioId);

        jdbcTemplate.update("""
                insert into movimentos_carteira(
                    usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
                    referencia_tipo, referencia_id, descricao, data_movimento,
                    saldo_resultante, idempotency_key
                ) values (?, ?, 'ENTRADA', 50.00, 50.00, 'CARTEIRA_AJUSTE',
                    'CARTEIRA', ?, 'Ajuste manual', current_timestamp, 150.00, 'idem-it-001')
                """, usuarioId, carteiraId, carteiraId);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into movimentos_carteira(
                    usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
                    data_movimento, saldo_resultante
                ) values (?, ?, 'ENTRADA', 0.00, 0.00, 'CARTEIRA_AJUSTE',
                    current_timestamp, 100.00)
                """, usuarioId, carteiraId));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into movimentos_carteira(
                    usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
                    data_movimento, saldo_resultante
                ) values (?, 999999, 'ENTRADA', 10.00, 10.00, 'CARTEIRA_AJUSTE',
                    current_timestamp, 110.00)
                """, usuarioId));
    }

    @Test
    void queryReconciliacaoRodaEmPostgresReal() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Recon', 'recon-it@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long carteiraId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, tipo, saldo, usuario_id, version) values ('Principal', 'DINHEIRO', 150.00, ?, 0) returning id",
                Long.class,
                usuarioId);

        jdbcTemplate.update("""
                insert into movimentos_carteira(
                    usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
                    referencia_tipo, referencia_id, descricao, data_movimento,
                    saldo_resultante, idempotency_key
                ) values
                    (?, ?, 'ENTRADA', 50.00, 50.00, 'CARTEIRA_AJUSTE',
                    'CARTEIRA', ?, 'Ajuste manual', current_timestamp, 50.00, 'recon-it-001'),
                    (?, ?, 'ENTRADA', 100.00, 100.00, 'CARTEIRA_AJUSTE',
                    'CARTEIRA', ?, 'Ajuste manual', current_timestamp, 150.00, 'recon-it-002')
                """, usuarioId, carteiraId, carteiraId, usuarioId, carteiraId, carteiraId);

        Map<String, Object> result = jdbcTemplate.queryForMap("""
                select c.id as carteira_id,
                       c.usuario_id as usuario_id,
                       c.saldo as saldo_materializado,
                       coalesce(sum(m.valor_assinado), 0) as saldo_ledger,
                       c.saldo - coalesce(sum(m.valor_assinado), 0) as diferenca
                from carteiras c
                left join movimentos_carteira m on m.carteira_id = c.id
                where c.usuario_id = ? and c.id = ?
                group by c.id, c.usuario_id, c.saldo
                """, usuarioId, carteiraId);

        assertEquals(carteiraId, ((Number) result.get("carteira_id")).longValue());
        assertEquals(usuarioId, ((Number) result.get("usuario_id")).longValue());
        assertBigDecimalEquals(new BigDecimal("150.00"), (BigDecimal) result.get("saldo_materializado"));
        assertBigDecimalEquals(new BigDecimal("150.00"), (BigDecimal) result.get("saldo_ledger"));
        assertBigDecimalEquals(BigDecimal.ZERO, (BigDecimal) result.get("diferenca"));
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
