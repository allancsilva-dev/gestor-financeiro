package com.gestor.financeiro;

import com.gestor.financeiro.service.TransferenciaService;
import com.gestor.financeiro.service.TransferirCommand;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-F2-04 — Concorrencia em PostgreSQL real: transferencias opostas em
 * paralelo nao geram deadlock (locks em ordem deterministica de id) e a soma
 * dos saldos e preservada; retry idempotente concorrente nao duplica movimento.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class TransferenciaConcorrenciaIT {

    private static PostgreSQLContainer<?> postgres;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferenciaService transferenciaService;

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
    void transferenciasOpostasEmParaleloNaoDeadlockamESomaEPreservada() throws Exception {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Conc', 'conc-f2@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long contaA = novaConta(usuarioId, "A", "1000.00");
        Long contaB = novaConta(usuarioId, "B", "1000.00");

        int rodadas = 10;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> {
            inicio.await();
            for (int i = 0; i < rodadas; i++) {
                transferenciaService.transferir(new TransferirCommand(
                        usuarioId, contaA, contaB, new BigDecimal("10.00"), "A->B", null, null));
            }
            return null;
        }));
        futures.add(executor.submit(() -> {
            inicio.await();
            for (int i = 0; i < rodadas; i++) {
                transferenciaService.transferir(new TransferirCommand(
                        usuarioId, contaB, contaA, new BigDecimal("10.00"), "B->A", null, null));
            }
            return null;
        }));

        inicio.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS), "threads não terminaram (possível deadlock)");
        for (Future<?> f : futures) {
            f.get(); // propaga excecao (deadlock geraria CannotAcquireLockException)
        }

        BigDecimal soma = jdbcTemplate.queryForObject(
                "select sum(saldo) from carteiras where usuario_id = ?", BigDecimal.class, usuarioId);
        assertEquals(0, new BigDecimal("2000.00").compareTo(soma));

        // cada rodada gerou exatamente 1 par de movimentos vinculados a operacao
        Integer movimentos = jdbcTemplate.queryForObject(
                "select count(*) from movimentos_carteira where usuario_id = ? and operacao_id is not null",
                Integer.class, usuarioId);
        assertEquals(rodadas * 4, movimentos);

        // reconciliacao: saldo = saldo inicial (1000, sem movimento) + soma do ledger
        Integer divergentes = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select c.id
                    from carteiras c
                    left join movimentos_carteira m on m.carteira_id = c.id
                    where c.usuario_id = ?
                    group by c.id, c.saldo
                    having c.saldo <> 1000.00 + coalesce(sum(m.valor_assinado), 0)
                ) d
                """, Integer.class, usuarioId);
        assertEquals(0, divergentes);
    }

    @Test
    void retryIdempotenteConcorrenteNaoDuplicaMovimento() throws Exception {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo) values ('Idem', 'idem-f2@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long origem = novaConta(usuarioId, "Origem", "500.00");
        Long destino = novaConta(usuarioId, "Destino", "0.00");

        int tentativas = 4;
        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            futures.add(executor.submit(() -> {
                inicio.await();
                try {
                    transferenciaService.transferir(new TransferirCommand(
                            usuarioId, origem, destino, new BigDecimal("50.00"),
                            "Retry", "tr-conc-idem", null));
                } catch (Exception e) {
                    // corrida de insercao da mesma chave pode conflitar (409); nunca duplica
                }
                return null;
            }));
        }

        inicio.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            f.get();
        }

        BigDecimal saldoOrigem = jdbcTemplate.queryForObject(
                "select saldo from carteiras where id = ?", BigDecimal.class, origem);
        BigDecimal saldoDestino = jdbcTemplate.queryForObject(
                "select saldo from carteiras where id = ?", BigDecimal.class, destino);
        assertEquals(0, new BigDecimal("450.00").compareTo(saldoOrigem));
        assertEquals(0, new BigDecimal("50.00").compareTo(saldoDestino));

        Integer operacoes = jdbcTemplate.queryForObject(
                "select count(*) from operacoes_financeiras where usuario_id = ? and idempotency_key = 'tr-conc-idem'",
                Integer.class, usuarioId);
        assertEquals(1, operacoes);
    }

    private Long novaConta(Long usuarioId, String nome, String saldo) {
        return jdbcTemplate.queryForObject(
                "insert into carteiras(nome, tipo, subtipo, saldo, usuario_id, version) values (?, 'CONTA_BANCARIA', 'CORRENTE', ?::numeric, ?, 0) returning id",
                Long.class, nome, saldo, usuarioId);
    }
}
