package com.gestor.financeiro;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PR-F2-01 — Invariantes do baseline das métricas oficiais (ADR-0013/ADR-0015).
 *
 * Executa, contra PostgreSQL real, as mesmas agregações de
 * scripts/baseline-metricas-fase2.sql sobre um cenário semeado e comprova que:
 *  A1  disponível agora == SUM(carteiras.saldo);
 *  A2  reservado == SUM(metas.valor_reservado) das metas não arquivadas;
 *  A3  dívidas == SUM(carteiras.saldo) dos passivos de cartão;
 *  C1  saldo materializado == soma do ledger (diferença zero);
 *  C6  saldo passivo == soma dos lançamentos de faturas não pagas.
 * Este snapshot é a referência "antes" de toda migration da Fase 2.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class BaselineMetricasFase2IT {

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
    void baselineCalculaMetricasEInvariantesSobreCenarioSemeado() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Baseline', 'baseline-f2@teste.com', 'x', 0, false) returning id",
                Long.class);

        // Caixa: duas carteiras com ledger consistente
        Long carteiraA = inserirCarteiraComLedger(usuarioId, "Corrente", new BigDecimal("1000.00"), "bl-f2-a");
        Long carteiraB = inserirCarteiraComLedger(usuarioId, "Poupanca", new BigDecimal("500.00"), "bl-f2-b");

        // Metas: uma ativa com reserva, uma arquivada sem reserva (fora do baseline)
        jdbcTemplate.update(
                "insert into metas(usuario_id, nome, valor_total, valor_reservado, ativa, status, version) values (?, 'Viagem', 2000.00, 300.00, true, 'ATIVA', 0)",
                usuarioId);
        jdbcTemplate.update(
                "insert into metas(usuario_id, nome, valor_total, valor_reservado, ativa, status, version) values (?, 'Antiga', 100.00, 0.00, false, 'ARQUIVADA', 0)",
                usuarioId);

        // Cartao: passivo do ledger casado com lancamentos de fatura nao paga (C6)
        Long passivoId = inserirCarteiraPassivaComLedger(usuarioId, new BigDecimal("250.00"));
        Long contaId = jdbcTemplate.queryForObject(
                "insert into contas(usuario_id, nome, limite_total, dia_fechamento, dia_vencimento, ativo, conta_financeira_id, version) values (?, 'Cartao', 1000, 5, 12, true, ?, 0) returning id",
                Long.class, usuarioId, passivoId);
        Long faturaId = jdbcTemplate.queryForObject(
                "insert into faturas_cartao(usuario_id, conta_id, mes, ano, status) values (?, ?, 7, 2026, 'ABERTA') returning id",
                Long.class, usuarioId, contaId);
        Long transacaoId = jdbcTemplate.queryForObject(
                "insert into transacoes(usuario_id, conta_id, descricao, valor_total, tipo, data, status) values (?, ?, 'Compra cartao', 250.00, 'SAIDA', current_date, 'PENDENTE') returning id",
                Long.class, usuarioId, contaId);
        jdbcTemplate.update(
                "insert into fatura_lancamentos(fatura_id, transacao_id, descricao, valor, data_compra, tipo) values (?, ?, 'Compra cartao', 250.00, current_date, 'COMPRA')",
                faturaId, transacaoId);

        // A1 — disponivel agora
        BigDecimal disponivel = jdbcTemplate.queryForObject(
                "select sum(saldo) from carteiras where usuario_id = ? and natureza='ATIVO' and liquidez='IMEDIATA'",
                BigDecimal.class, usuarioId);
        assertBigDecimalEquals(new BigDecimal("1500.00"), disponivel);

        // A2 — reservado (metas nao arquivadas)
        BigDecimal reservado = jdbcTemplate.queryForObject(
                "select sum(coalesce(valor_reservado,0)) from metas where usuario_id = ? and status <> 'ARQUIVADA'",
                BigDecimal.class, usuarioId);
        assertBigDecimalEquals(new BigDecimal("300.00"), reservado);

        // A3 — dividas de cartao
        BigDecimal dividas = jdbcTemplate.queryForObject(
                "select sum(saldo) from carteiras where usuario_id = ? and natureza = 'PASSIVO'",
                BigDecimal.class, usuarioId);
        assertBigDecimalEquals(new BigDecimal("250.00"), dividas);

        // C1 — saldo materializado == ledger, por carteira
        for (Long carteiraId : new Long[] { carteiraA, carteiraB }) {
            Map<String, Object> recon = jdbcTemplate.queryForMap("""
                    select c.saldo - coalesce(sum(m.valor_assinado), 0) as diferenca
                    from carteiras c
                    left join movimentos_carteira m on m.carteira_id = c.id
                    where c.id = ?
                    group by c.id, c.saldo
                    """, carteiraId);
            assertBigDecimalEquals(BigDecimal.ZERO, (BigDecimal) recon.get("diferenca"));
        }

        // C6 — saldo passivo == lancamentos de faturas nao pagas
        Map<String, Object> passivo = jdbcTemplate.queryForMap("""
                select coalesce(cf.saldo,0) - coalesce((
                         select sum(fl.valor)
                         from faturas_cartao fc
                         join fatura_lancamentos fl on fl.fatura_id = fc.id
                         where fc.conta_id = ct.id and fc.status <> 'PAGA'
                       ), 0) as diferenca
                from contas ct join carteiras cf on cf.id=ct.conta_financeira_id
                where ct.id = ?
                """, contaId);
        assertBigDecimalEquals(BigDecimal.ZERO, (BigDecimal) passivo.get("diferenca"));
    }

    private Long inserirCarteiraComLedger(Long usuarioId, String nome, BigDecimal saldo, String idemPrefix) {
        Long carteiraId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values (?, 'CORRENTE', ?, ?, 0) returning id",
                Long.class, nome, saldo, usuarioId);
        jdbcTemplate.update("""
                insert into movimentos_carteira(
                    usuario_id, carteira_id, tipo, valor, valor_assinado, origem,
                    referencia_tipo, referencia_id, descricao, data_movimento,
                    saldo_resultante, idempotency_key
                ) values (?, ?, 'ENTRADA', ?, ?, 'CARTEIRA_AJUSTE',
                    'CARTEIRA', ?, 'Saldo inicial baseline', current_timestamp, ?, ?)
                """, usuarioId, carteiraId, saldo, saldo, carteiraId, saldo, idemPrefix + "-inicial");
        return carteiraId;
    }

    private Long inserirCarteiraPassivaComLedger(Long usuarioId, BigDecimal saldo) {
        Long carteiraId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, subtipo, natureza, saldo, usuario_id, version) values ('Cartao', 'CARTAO', 'PASSIVO', ?, ?, 0) returning id",
                Long.class, saldo, usuarioId);
        jdbcTemplate.update("""
                insert into movimentos_carteira(usuario_id, carteira_id, tipo, valor, valor_assinado,
                    origem, descricao, data_movimento, saldo_resultante, idempotency_key)
                values (?, ?, 'ENTRADA', ?, ?, 'FATURA_CARTAO', 'Compra cartao', current_timestamp, ?, 'bl-f2-cartao')
                """, usuarioId, carteiraId, saldo, saldo, saldo);
        return carteiraId;
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
