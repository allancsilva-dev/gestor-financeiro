package com.gestor.financeiro;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("postgres-it")
class PostgresMigrationIT {

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

    @Test
    void flywayAplicaMigrationsEmPostgresLimpoEHibernateValidaSchema() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class);

        assertNotNull(appliedMigrations);
        assertTrue(appliedMigrations >= 24);
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

    @Test
    void checkConstraintsRejeitamValoresFinanceirosInvalidos() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Chk', 'chk-it@teste.com', 'x', 0, false) returning id",
                Long.class);

        // valor_total <= 0 rejeitado (chk_transacoes_valor_total_positivo)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into transacoes(usuario_id, descricao, valor_total, tipo, data, status) values (?, 'Invalida', 0, 'SAIDA', current_date, 'PENDENTE')",
                usuarioId));

        // tipo fora do dominio rejeitado (chk_transacoes_tipo)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into transacoes(usuario_id, descricao, valor_total, tipo, data, status) values (?, 'Invalida', 10, 'FOO', current_date, 'PENDENTE')",
                usuarioId));

        // linha valida continua passando
        int inserted = jdbcTemplate.update(
                "insert into transacoes(usuario_id, descricao, valor_total, tipo, data, status) values (?, 'Valida', 10.00, 'SAIDA', current_date, 'PENDENTE')",
                usuarioId);
        assertEquals(1, inserted);
    }

    @Test
    void uniqueFaturaLancamentoImpedeCompraAVistaDuplicada() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Fat', 'fat-it@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long contaId = jdbcTemplate.queryForObject(
                "insert into contas(usuario_id, nome, tipo, version) values (?, 'Cartao', 'CREDITO', 0) returning id",
                Long.class, usuarioId);
        Long faturaId = jdbcTemplate.queryForObject(
                "insert into faturas_cartao(usuario_id, conta_id, mes, ano, status) values (?, ?, 7, 2026, 'ABERTA') returning id",
                Long.class, usuarioId, contaId);
        Long transacaoId = jdbcTemplate.queryForObject(
                "insert into transacoes(usuario_id, descricao, valor_total, tipo, data, status) values (?, 'Compra', 100.00, 'SAIDA', current_date, 'PENDENTE') returning id",
                Long.class, usuarioId);

        // Compra a vista usa parcela_numero NULL
        jdbcTemplate.update(
                "insert into fatura_lancamentos(fatura_id, transacao_id, descricao, valor, data_compra, parcela_numero, total_parcelas, tipo) values (?, ?, 'Compra', 100.00, current_date, null, null, 'COMPRA')",
                faturaId, transacaoId);

        // Reinsercao identica deve ser barrada pelo indice funcional COALESCE(parcela_numero, 0)
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into fatura_lancamentos(fatura_id, transacao_id, descricao, valor, data_compra, parcela_numero, total_parcelas, tipo) values (?, ?, 'Compra', 100.00, current_date, null, null, 'COMPRA')",
                faturaId, transacaoId));
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
