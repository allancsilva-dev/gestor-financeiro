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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.service.ReconciliacaoGlobalService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private ReconciliacaoGlobalService reconciliacaoGlobalService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    /**
     * V68 — proveniência do lote.
     *
     * <p>Prova as duas metades da decisão: o formato do conector passa a ser aceito, e um lote de
     * conector não consegue se apresentar como envio manual. Sem a segunda metade, a coluna
     * {@code origin} seria só documentação — qualquer caminho que esquecesse de preenchê-la
     * gravaria 'UPLOAD' pelo default e ninguém notaria.</p>
     */
    @Test
    void v68AceitaLoteDeConectorERecusaOrigemInconsistente() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo)"
                        + " values ('Conector', 'conector-v68@teste.com', 'x', 0, false) returning id",
                Long.class);
        String hash = "b".repeat(64);

        jdbcTemplate.update(
                "insert into import_batches(usuario_id, format, origin, file_sha256) values (?, ?, ?, ?)",
                usuarioId, "OPEN_FINANCE", "CONNECTOR", hash);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into import_batches(usuario_id, format, origin, file_sha256) values (?, ?, ?, ?)",
                usuarioId, "OPEN_FINANCE", "UPLOAD", hash));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into import_batches(usuario_id, format, origin, file_sha256) values (?, ?, ?, ?)",
                usuarioId, "CSV", "SINCRONIA", hash));

        // Lote antigo continua válido e assume envio manual pelo default da coluna.
        jdbcTemplate.update(
                "insert into import_batches(usuario_id, format, file_sha256) values (?, ?, ?)",
                usuarioId, "CSV", hash);
        String origemDoLoteCsv = jdbcTemplate.queryForObject(
                "select origin from import_batches where usuario_id = ? and format = 'CSV'",
                String.class, usuarioId);
        assertEquals("UPLOAD", origemDoLoteCsv);

        jdbcTemplate.update("delete from import_batches where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from usuarios where id = ?", usuarioId);
    }

    /**
     * V70 — conexão, credencial e consentimento (ADR-0020).
     *
     * <p>Prova as invariantes que o banco precisa sustentar sozinho, porque são as que continuam
     * valendo mesmo se algum caminho de código esquecer delas: um consentimento ativo por conexão,
     * revogação sempre carimbada, e escopo dentro de uma lista fechada.</p>
     */
    @Test
    void v70SustentaConsentimentoUnicoAtivoERevogacaoCarimbada() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo)"
                        + " values ('Consent', 'consent-v70@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long provedorId = jdbcTemplate.queryForObject(
                "insert into open_finance_provedores(codigo, nome, tipo) values ('FAKE-V70', 'Fake', 'FAKE') returning id",
                Long.class);
        Long conexaoId = jdbcTemplate.queryForObject(
                "insert into conexoes_open_finance(usuario_id, provedor_id, status) values (?, ?, 'ATIVA') returning id",
                Long.class, usuarioId, provedorId);

        String inserirAtivo = "insert into consentimentos_open_finance(usuario_id, conexao_id, escopos,"
                + " status, concedido_em, expira_em) values (?, ?, ?, 'ATIVO', current_timestamp,"
                + " current_timestamp + interval '90 days')";
        jdbcTemplate.update(inserirAtivo, usuarioId, conexaoId, "ACCOUNTS,TRANSACTIONS");

        // Duas renovações concorrentes não podem deixar dois consentimentos ativos: revogar um
        // deixaria o outro valendo, e a revogação viraria promessa não cumprida.
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(inserirAtivo, usuarioId, conexaoId, "ACCOUNTS"));

        // Escopo fora da lista fechada não entra.
        String inserirAguardando = "insert into consentimentos_open_finance(usuario_id, conexao_id,"
                + " escopos, status, expira_em) values (?, ?, ?, 'AGUARDANDO',"
                + " current_timestamp + interval '90 days')";
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(inserirAguardando, usuarioId, conexaoId, "TUDO"));

        // Revogado sem quem e quando seria prova incompleta.
        String inserirRevogadoSemCarimbo = "insert into consentimentos_open_finance(usuario_id,"
                + " conexao_id, escopos, status, expira_em) values (?, ?, 'ACCOUNTS', 'REVOGADO',"
                + " current_timestamp + interval '90 days')";
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(inserirRevogadoSemCarimbo, usuarioId, conexaoId));

        // Revogar libera o índice parcial para o consentimento seguinte.
        jdbcTemplate.update("update consentimentos_open_finance set status = 'REVOGADO',"
                + " revogado_em = current_timestamp, revogado_por = 'TITULAR'"
                + " where conexao_id = ? and status = 'ATIVO'", conexaoId);
        jdbcTemplate.update(inserirAtivo, usuarioId, conexaoId, "ACCOUNTS");

        Integer historico = jdbcTemplate.queryForObject(
                "select count(*) from consentimentos_open_finance where conexao_id = ?", Integer.class, conexaoId);
        assertEquals(2, historico, "append-only: revogar nao apaga a linha anterior");

        jdbcTemplate.update("delete from consentimentos_open_finance where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from conexoes_open_finance where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from open_finance_provedores where id = ?", provedorId);
        jdbcTemplate.update("delete from usuarios where id = ?", usuarioId);
    }

    /** Apagar a conexão leva a credencial junto: segredo não sobrevive ao vínculo que o justificava. */
    @Test
    void v70RemoveCredencialComAConexao() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo)"
                        + " values ('Cred', 'cred-v70@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long provedorId = jdbcTemplate.queryForObject(
                "insert into open_finance_provedores(codigo, nome, tipo) values ('FAKE-CRED', 'Fake', 'FAKE') returning id",
                Long.class);
        Long conexaoId = jdbcTemplate.queryForObject(
                "insert into conexoes_open_finance(usuario_id, provedor_id, status) values (?, ?, 'ATIVA') returning id",
                Long.class, usuarioId, provedorId);
        jdbcTemplate.update("insert into conexao_credenciais(conexao_id, usuario_id,"
                + " access_token_cifrado, key_version, token_hmac) values (?, ?, 'cifrado', 'v1', ?)",
                conexaoId, usuarioId, "a".repeat(64));

        // HMAC é campo de lookup em hexadecimal; não é lugar para token em claro.
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into conexao_credenciais(conexao_id, usuario_id, token_hmac) values (?, ?, 'token-em-claro')",
                conexaoId, usuarioId));

        jdbcTemplate.update("delete from conexoes_open_finance where id = ?", conexaoId);
        Integer credenciais = jdbcTemplate.queryForObject(
                "select count(*) from conexao_credenciais where usuario_id = ?", Integer.class, usuarioId);
        assertEquals(0, credenciais);

        jdbcTemplate.update("delete from open_finance_provedores where id = ?", provedorId);
        jdbcTemplate.update("delete from usuarios where id = ?", usuarioId);
    }

    /**
     * V71 — conta conectada e sincronização.
     *
     * <p>Duas invariantes que o banco precisa sustentar sozinho: uma carteira nunca recebe duas
     * conexões ativas (duas fontes escrevendo no mesmo caixa produziriam divergência permanente sem
     * culpado identificável), e conta ativa tem exatamente um destino no ledger.</p>
     */
    @Test
    void v71ImpedeDuasConexoesNaMesmaCarteiraEExigeDestinoUnico() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome, email, senha, failed_attempts, onboarding_completo)"
                        + " values ('Sync', 'sync-v71@teste.com', 'x', 0, false) returning id",
                Long.class);
        Long carteiraId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version)"
                        + " values ('Conectada', 'DINHEIRO', 0.00, ?, 0) returning id",
                Long.class, usuarioId);
        Long provedorId = jdbcTemplate.queryForObject(
                "insert into open_finance_provedores(codigo, nome, tipo) values ('FAKE-V71', 'Fake', 'FAKE') returning id",
                Long.class);
        Long conexaoId = jdbcTemplate.queryForObject(
                "insert into conexoes_open_finance(usuario_id, provedor_id, status) values (?, ?, 'ATIVA') returning id",
                Long.class, usuarioId, provedorId);

        String inserirConta = "insert into contas_conectadas(usuario_id, conexao_id, external_account_id,"
                + " tipo, carteira_id) values (?, ?, ?, 'CORRENTE', ?)";
        jdbcTemplate.update(inserirConta, usuarioId, conexaoId, "ext-1", carteiraId);

        // Segunda conexão ativa apontando para a mesma carteira: barrada pelo índice parcial.
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(inserirConta, usuarioId, conexaoId, "ext-2", carteiraId));

        // Conta ativa sem destino no ledger: a sincronização não saberia onde lançar.
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into contas_conectadas(usuario_id, conexao_id, external_account_id, tipo)"
                        + " values (?, ?, 'ext-3', 'CORRENTE')", usuarioId, conexaoId));

        // Mesma conta externa duas vezes na mesma conexão.
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(inserirConta, usuarioId, conexaoId, "ext-1", null));

        Long contaConectadaId = jdbcTemplate.queryForObject(
                "select id from contas_conectadas where usuario_id = ?", Long.class, usuarioId);

        // Log de sincronização: job_key é a terceira camada de reentrância.
        String inserirExecucao = "insert into sync_execucoes(usuario_id, conta_conectada_id, job_key, tipo, status)"
                + " values (?, ?, ?, 'INCREMENTAL', 'OK')";
        jdbcTemplate.update(inserirExecucao, usuarioId, contaConectadaId, "of:sync:1:2026-09-01:2026-09-02");
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                inserirExecucao, usuarioId, contaConectadaId, "of:sync:1:2026-09-01:2026-09-02"));

        // Erro sem código seria log inútil no dia do incidente.
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into sync_execucoes(usuario_id, conta_conectada_id, job_key, tipo, status)"
                        + " values (?, ?, 'of:sync:erro', 'INCREMENTAL', 'ERRO')", usuarioId, contaConectadaId));

        // Mais registros novos do que recebidos seria contagem impossível.
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into sync_execucoes(usuario_id, conta_conectada_id, job_key, tipo, status,"
                        + " registros_recebidos, registros_novos) values (?, ?, 'of:sync:contagem',"
                        + " 'INCREMENTAL', 'OK', 2, 5)", usuarioId, contaConectadaId));

        jdbcTemplate.update("delete from sync_execucoes where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from contas_conectadas where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from conexoes_open_finance where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from open_finance_provedores where id = ?", provedorId);
        jdbcTemplate.update("delete from carteiras where usuario_id = ?", usuarioId);
        jdbcTemplate.update("delete from usuarios where id = ?", usuarioId);
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
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Principal', 'DINHEIRO', 100.00, ?, 0) returning id",
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
                "insert into carteiras(nome, subtipo, saldo, usuario_id, version) values ('Principal', 'DINHEIRO', 150.00, ?, 0) returning id",
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
        Long passivoId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome, subtipo, natureza, saldo, usuario_id, version) values ('Cartao', 'CARTAO', 'PASSIVO', 0, ?, 0) returning id",
                Long.class, usuarioId);
        Long contaId = jdbcTemplate.queryForObject(
                "insert into contas(usuario_id, nome, limite_total, dia_fechamento, dia_vencimento, ativo, conta_financeira_id, version) values (?, 'Cartao', 1000, 5, 12, true, ?, 0) returning id",
                Long.class, usuarioId, passivoId);
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

    @Test
    void reconciliacaoGlobalRodaSobreSchemaV41ComRolloverCofreETransacaoPendente() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome,email,senha) values ('Global IT','global-v41-it@teste.com','x') returning id",
                Long.class);
        Long caixaId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome,subtipo,natureza,saldo,usuario_id,version) values ('Caixa','CORRENTE','ATIVO',0,?,0) returning id",
                Long.class, usuarioId);
        Long passivoId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome,subtipo,natureza,saldo,usuario_id,version) values ('Passivo','CARTAO','PASSIVO',70,?,0) returning id",
                Long.class, usuarioId);
        Long cofreId = jdbcTemplate.queryForObject(
                "insert into carteiras(nome,subtipo,natureza,saldo,usuario_id,version) values ('Cofre','COFRE','ATIVO',30,?,0) returning id",
                Long.class, usuarioId);
        jdbcTemplate.update("""
                insert into movimentos_carteira(usuario_id,carteira_id,tipo,valor,valor_assinado,
                  origem,descricao,data_movimento,saldo_resultante)
                values (?,?,'ENTRADA',70,70,'FATURA_CARTAO','it',current_timestamp,70),
                       (?,?,'ENTRADA',30,30,'BACKFILL','it',current_timestamp,30)
                """, usuarioId, passivoId, usuarioId, cofreId);
        Long cartaoId = jdbcTemplate.queryForObject("""
                insert into contas(usuario_id,nome,limite_total,dia_fechamento,dia_vencimento,
                  ativo,conta_financeira_id,version)
                values (?,'Inativo com dívida',1000,5,12,false,?,0) returning id
                """, Long.class, usuarioId, passivoId);
        Long origemId = jdbcTemplate.queryForObject("""
                insert into faturas_cartao(usuario_id,conta_id,mes,ano,valor_total,valor_pago,status)
                values (?,?,6,2026,20,0,'FECHADA') returning id
                """, Long.class, usuarioId, cartaoId);
        Long destinoId = jdbcTemplate.queryForObject("""
                insert into faturas_cartao(usuario_id,conta_id,mes,ano,valor_total,valor_pago,status)
                values (?,?,7,2026,70,0,'ABERTA') returning id
                """, Long.class, usuarioId, cartaoId);
        Long compraId = jdbcTemplate.queryForObject("""
                insert into transacoes(usuario_id,conta_id,descricao,valor_total,tipo,data,status,
                  ativa,estado_conciliacao)
                values (?,?,'Compra',50,'SAIDA',current_date,'PENDENTE',true,'CONCILIADA') returning id
                """, Long.class, usuarioId, cartaoId);
        jdbcTemplate.update("""
                insert into fatura_lancamentos(fatura_id,transacao_id,descricao,valor,data_compra,tipo)
                values (?,?,'Compra',50,current_date,'COMPRA')
                """, destinoId, compraId);
        jdbcTemplate.update("""
                insert into fatura_lancamentos(fatura_id,fatura_origem_id,descricao,valor,data_compra,tipo)
                values (?,?,'Rollover',20,current_date,'CREDITO_ANTERIOR')
                """, destinoId, origemId);
        jdbcTemplate.update("""
                insert into metas(usuario_id,nome,valor_total,valor_reservado,cofre_id,modalidade,
                  ativa,status,version)
                values (?,'Meta arquivada',100,30,?,'COFRE_REAL',false,'ARQUIVADA',0)
                """, usuarioId, cofreId);
        jdbcTemplate.update("""
                insert into transacoes(usuario_id,carteira_id,descricao,valor_total,tipo,data,status,
                  ativa,estado_conciliacao)
                values (? ,?,'Com caixa',1,'ENTRADA',current_date,'PENDENTE',true,'CONCILIADA'),
                       (?,null,'Importada',1,'ENTRADA',current_date,'PENDENTE',true,'PENDENTE_CONCILIACAO')
                """, usuarioId, caixaId, usuarioId);

        ReconciliacaoGlobalResponse report = reconciliacaoGlobalService.reconciliarUsuario(usuarioId);
        assertEquals(ReconciliacaoGlobalResponse.Status.OK, report.status());
        assertEquals(0, report.divergencias());
        // Cinco invariantes desde a inclusão de CATEGORIA_VALOR_GASTO.
        assertEquals(5, report.resumo().size());
    }

    @Test
    void repeatableReadMantemSnapshotDuranteEscritaConcorrente() {
        Long usuarioId = jdbcTemplate.queryForObject(
                "insert into usuarios(nome,email,senha) values ('Snapshot','snapshot-it@teste.com','x') returning id",
                Long.class);
        TransactionTemplate snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.executeWithoutResult(status -> {
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "select count(*) from carteiras where usuario_id=?", Long.class, usuarioId));
            var executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> jdbcTemplate.update("""
                        insert into carteiras(nome,subtipo,natureza,saldo,usuario_id,version)
                        values ('Concorrente','CORRENTE','ATIVO',0,?,0)
                        """, usuarioId)).get(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            } finally {
                executor.shutdownNow();
            }
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "select count(*) from carteiras where usuario_id=?", Long.class, usuarioId));
        });
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from carteiras where usuario_id=?", Long.class, usuarioId));
    }

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
