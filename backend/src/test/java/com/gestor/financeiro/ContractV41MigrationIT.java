package com.gestor.financeiro;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prova o contract destrutivo V40 -> V41 em PostgreSQL real. */
class ContractV41MigrationIT {

    private static PostgreSQLContainer<?> postgres;
    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void start() {
        String externalUrl = System.getenv("POSTGRES_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            url = externalUrl;
            user = getenv("POSTGRES_IT_USERNAME", "postgres");
            password = getenv("POSTGRES_IT_PASSWORD", "postgres");
            return;
        }
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("contract_v41_it")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
        url = postgres.getJdbcUrl();
        user = postgres.getUsername();
        password = postgres.getPassword();
    }

    @AfterAll
    static void stop() {
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void migrateToV40() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway("40").migrate();
    }

    @Test
    void migraV40PelosTresCaminhosEPreservaLedgerESchema() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "tres-caminhos@teste.com");
            long carteiraTransacao = insertCarteira(st, usuario, "Conta da transação", "PAGAMENTO", "Banco A");
            insertCarteira(st, usuario, "Poupança exata", "POUPANCA", "Banco B");
            long passivo = insertCarteiraCartao(st, usuario, "Cartão principal");
            insertCartao(st, usuario, "Cartão principal", passivo);

            long contaTransacao = insertContaLegada(st, usuario, "Débito legado", "DEBITO", "Banco A");
            insertTransacao(st, usuario, contaTransacao, carteiraTransacao);
            insertContaLegada(st, usuario, " Poupança exata ", "POUPANCA", " banco b ");
            insertContaLegada(st, usuario, "Dinheiro novo", "DINHEIRO", null);
        }

        flyway(null).migrate();

        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            assertEquals(1, scalar(st, "SELECT count(*) FROM contas"));
            assertEquals(4, scalar(st, "SELECT count(*) FROM carteiras"));
            assertEquals(1, scalar(st, "SELECT count(*) FROM transacoes WHERE conta_id IS NULL AND carteira_id IS NOT NULL"));
            assertEquals(1, scalar(st, "SELECT count(*) FROM carteiras WHERE nome='Dinheiro novo' AND subtipo='DINHEIRO' AND saldo=0"));
            assertEquals(0, scalar(st, "SELECT count(*) FROM information_schema.columns WHERE table_name='contas' AND column_name IN ('tipo','saldo_atual','valor_gasto')"));
            assertEquals(0, scalar(st, "SELECT count(*) FROM information_schema.columns WHERE table_name='carteiras' AND column_name='tipo'"));
            assertEquals(0, scalar(st, "SELECT count(*) FROM carteiras cf WHERE cf.saldo <> COALESCE((SELECT sum(valor_assinado) FROM movimentos_carteira mc WHERE mc.carteira_id=cf.id),0)"));
            assertEquals(1, scalar(st, "SELECT count(*) FROM flyway_schema_history WHERE version='41' AND success=true"));
        }
    }

    @Test
    void saldoLegadoNaoZeroAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "saldo-legado@teste.com");
            long conta = insertContaLegada(st, usuario, "Inválida", "DINHEIRO", null);
            st.executeUpdate("UPDATE contas SET saldo_atual=1 WHERE id=" + conta);
        }
        assertRollbackV40();
    }

    @Test
    void ownershipDivergenteAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long donoConta = insertUsuario(st, "dono-conta@teste.com");
            long outro = insertUsuario(st, "outro@teste.com");
            long carteiraOutro = insertCarteira(st, outro, "Alheia", "PAGAMENTO", null);
            long conta = insertContaLegada(st, donoConta, "Débito", "DEBITO", null);
            insertTransacao(st, donoConta, conta, carteiraOutro);
        }
        assertRollbackV40();
    }

    @Test
    void ambiguidadeDeReusoAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "ambiguo@teste.com");
            insertCarteira(st, usuario, "Mesmo nome", "POUPANCA", "Banco");
            insertCarteira(st, usuario, " mesmo nome ", "POUPANCA", " banco ");
            insertContaLegada(st, usuario, "Mesmo nome", "POUPANCA", "Banco");
        }
        assertRollbackV40();
    }

    @Test
    void passivoDivergenteAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "passivo@teste.com");
            long passivo = insertCarteiraCartao(st, usuario, "Cartão");
            long cartao = insertCartao(st, usuario, "Cartão", passivo);
            st.executeUpdate("UPDATE carteiras SET saldo=10 WHERE id=" + passivo);
            st.executeUpdate("INSERT INTO movimentos_carteira(usuario_id,carteira_id,tipo,valor,valor_assinado,origem,descricao,data_movimento,saldo_resultante) " +
                    "VALUES (" + usuario + "," + passivo + ",'ENTRADA',10,10,'FATURA_CARTAO','divergencia',current_timestamp,10)");
            assertTrue(cartao > 0);
        }
        assertRollbackV40();
    }

    @Test
    void ledgerDivergenteAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "ledger-divergente@teste.com");
            long carteira = insertCarteira(st, usuario, "Sem movimento", "DINHEIRO", null);
            st.executeUpdate("UPDATE carteiras SET saldo=5 WHERE id=" + carteira);
        }
        assertRollbackV40();
    }

    @Test
    void cartaoSemPareamentoAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "sem-pareamento@teste.com");
            st.executeUpdate("INSERT INTO contas(usuario_id,nome,tipo,limite_total,valor_gasto,saldo_atual,dia_fechamento,dia_vencimento,ativo,version) " +
                    "VALUES (" + usuario + ",'Sem passivo','CREDITO',1000,0,0,5,12,true,0)");
        }
        assertRollbackV40();
    }

    @Test
    void tipoLegadoDesconhecidoAbortaERestauraV40Integralmente() throws SQLException {
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            long usuario = insertUsuario(st, "tipo-desconhecido@teste.com");
            st.executeUpdate("ALTER TABLE contas DROP CONSTRAINT chk_contas_tipo");
            insertContaLegada(st, usuario, "Desconhecida", "OUTRO", null);
        }
        assertRollbackV40();
    }

    private void assertRollbackV40() throws SQLException {
        assertThrows(FlywayException.class, () -> flyway(null).migrate());
        try (Connection connection = connection(); Statement st = connection.createStatement()) {
            assertTrue(columnExists(st, "contas", "tipo"));
            assertTrue(columnExists(st, "contas", "saldo_atual"));
            assertTrue(columnExists(st, "carteiras", "tipo"));
            assertFalse(columnExists(st, "contas", "cartao_id"));
            assertEquals(0, scalar(st, "SELECT count(*) FROM flyway_schema_history WHERE version='41' AND success=true"));
        }
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) config.target(target);
        return config.load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static long insertUsuario(Statement st, String email) throws SQLException {
        return returning(st, "INSERT INTO usuarios(nome,email,senha) VALUES ('V41','" + email + "','hash') RETURNING id");
    }

    private static long insertCarteira(Statement st, long usuario, String nome, String subtipo, String banco) throws SQLException {
        String tipo = switch (subtipo) {
            case "DINHEIRO" -> "DINHEIRO";
            case "POUPANCA" -> "POUPANCA";
            default -> "CONTA_BANCARIA";
        };
        return returning(st, "INSERT INTO carteiras(nome,tipo,saldo,banco,usuario_id,natureza,subtipo,liquidez,origem_dados,estado_conciliacao,moeda,version) VALUES (" +
                quote(nome) + "," + quote(tipo) + ",0," + quote(banco) + "," + usuario + ",'ATIVO'," + quote(subtipo) + ",'IMEDIATA','MANUAL','CONCILIADA','BRL',0) RETURNING id");
    }

    private static long insertCarteiraCartao(Statement st, long usuario, String nome) throws SQLException {
        return returning(st, "INSERT INTO carteiras(nome,tipo,saldo,usuario_id,natureza,subtipo,liquidez,origem_dados,estado_conciliacao,moeda,version) VALUES (" +
                quote(nome) + ",'CARTAO',0," + usuario + ",'PASSIVO','CARTAO','IMEDIATA','MANUAL','CONCILIADA','BRL',0) RETURNING id");
    }

    private static long insertCartao(Statement st, long usuario, String nome, long passivo) throws SQLException {
        return returning(st, "INSERT INTO contas(usuario_id,nome,tipo,limite_total,valor_gasto,saldo_atual,dia_fechamento,dia_vencimento,ativo,conta_financeira_id,version) VALUES (" +
                usuario + "," + quote(nome) + ",'CREDITO',1000,0,0,5,12,true," + passivo + ",0) RETURNING id");
    }

    private static long insertContaLegada(Statement st, long usuario, String nome, String tipo, String banco) throws SQLException {
        return returning(st, "INSERT INTO contas(usuario_id,nome,tipo,limite_total,valor_gasto,saldo_atual,dia_fechamento,dia_vencimento,ativo,banco,version) VALUES (" +
                usuario + "," + quote(nome) + "," + quote(tipo) + ",0,0,0,1,1,true," + quote(banco) + ",0) RETURNING id");
    }

    private static void insertTransacao(Statement st, long usuario, long conta, long carteira) throws SQLException {
        st.executeUpdate("INSERT INTO transacoes(usuario_id,conta_id,carteira_id,descricao,valor_total,tipo,data,status,parcelado,recorrente,ativa,estado_conciliacao) VALUES (" +
                usuario + "," + conta + "," + carteira + ",'Compra',10,'SAIDA',current_date,'PENDENTE',false,false,true,'CONCILIADA')");
    }

    private static long returning(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }

    private static long scalar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }

    private static boolean columnExists(Statement st, String table, String column) throws SQLException {
        return scalar(st, "SELECT count(*) FROM information_schema.columns WHERE table_name='" + table + "' AND column_name='" + column + "'") == 1;
    }

    private static String quote(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private static String getenv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
