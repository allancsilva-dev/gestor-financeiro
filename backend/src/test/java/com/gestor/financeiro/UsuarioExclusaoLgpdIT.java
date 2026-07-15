package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.ExecucaoRecorrencia;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.ExecucaoRecorrenciaRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.UsuarioExclusaoService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regressão LGPD (PROB-0076 / ADR-0007) contra PostgreSQL real:
 * 1) teste-guardião: toda tabela alcançável por FK a partir de `usuarios` precisa estar no
 *    manifesto de exclusão do titular;
 * 2) exclusão de titular com grafo completo (recorrência realizada, pulada e com falha de
 *    saldo) termina sem violação de FK, zera os dados do titular, remove arquivos e preserva
 *    integralmente os dados de outros usuários.
 */
@SpringBootTest
@ActiveProfiles("postgres-it")
class UsuarioExclusaoLgpdIT {

    /** Tabelas fora do manifesto aceitas explicitamente (nenhuma hoje). */
    private static final Set<String> TABELAS_ACEITAS_FORA_DO_MANIFESTO = Set.of();

    private static PostgreSQLContainer<?> postgres;
    private static final Path UPLOAD_DIR;

    static {
        try {
            UPLOAD_DIR = Files.createTempDirectory("lgpd-uploads-it");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioExclusaoService usuarioExclusaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired ExecucaoRecorrenciaRepository execucaoRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired MetaRepository metaRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> UPLOAD_DIR.toString());

        String externalUrl = System.getenv("POSTGRES_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> getenvOrDefault("POSTGRES_IT_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> getenvOrDefault("POSTGRES_IT_PASSWORD", "postgres"));
            return;
        }

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("gestor_financeiro_lgpd_it")
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
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    @Test
    void guardiao_todaTabelaAlcancavelPorFkAPartirDeUsuariosEstaNoManifesto() {
        List<Map<String, Object>> fks = jdbcTemplate.queryForList("""
                SELECT tc.table_name AS origem, ccu.table_name AS destino
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.constraint_schema = tc.constraint_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                """);

        Set<String> alcancaveis = new HashSet<>();
        alcancaveis.add("usuarios");
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (Map<String, Object> fk : fks) {
                String origem = fk.get("origem").toString();
                String destino = fk.get("destino").toString();
                if (alcancaveis.contains(destino) && alcancaveis.add(origem)) {
                    mudou = true;
                }
            }
        }

        Set<String> manifesto = new HashSet<>();
        for (UsuarioExclusaoService.DeleteTitular delete : UsuarioExclusaoService.MANIFESTO_EXCLUSAO) {
            manifesto.add(delete.tabela());
        }

        Set<String> foraDoManifesto = new HashSet<>(alcancaveis);
        foraDoManifesto.removeAll(manifesto);
        foraDoManifesto.removeAll(TABELAS_ACEITAS_FORA_DO_MANIFESTO);

        assertThat(foraDoManifesto)
                .withFailMessage("Tabelas com dados alcançáveis do titular fora do manifesto de exclusão LGPD: %s. "
                        + "Adicione-as a UsuarioExclusaoService.MANIFESTO_EXCLUSAO (ou aceite explicitamente no teste).",
                        foraDoManifesto)
                .isEmpty();
    }

    @Test
    void excluirTitularComRecorrenciasRemoveTudoEPreservaOutroUsuario() throws IOException {
        Usuario titular = usuarioRepository.save(TestDataFactory.usuario("Titular", "titular-lgpd@teste.com", "hash"));
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario("Outro", "outro-lgpd@teste.com", "hash"));

        montarGrafo(titular);
        montarGrafo(outro);

        Path arquivoTitular = UPLOAD_DIR.resolve(titular.getId().toString()).resolve("comprovante.pdf");
        Files.createDirectories(arquivoTitular.getParent());
        Files.writeString(arquivoTitular, "conteudo");

        usuarioExclusaoService.excluirConta(titular.getId());

        for (String tabela : tabelasComColunaUsuarioId()) {
            Integer restantes = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + tabela + " WHERE usuario_id = ?", Integer.class, titular.getId());
            assertThat(restantes)
                    .withFailMessage("Tabela %s ainda tem %s linha(s) do titular excluído", tabela, restantes)
                    .isZero();
        }
        assertThat(usuarioRepository.findById(titular.getId())).isEmpty();
        assertThat(Files.exists(arquivoTitular.getParent())).isFalse();

        assertThat(usuarioRepository.findById(outro.getId())).isPresent();
        assertThat(execucaoRepository.findAll())
                .hasSize(3)
                .allMatch(e -> e.getUsuario().getId().equals(outro.getId()));
        assertThat(transacaoRepository.findByUsuarioId(outro.getId())).hasSize(2);
        assertThat(contaFixaRepository.findByUsuarioIdAndAtivoTrue(outro.getId())).hasSize(1);
        assertThat(metaRepository.findByUsuarioIdAndAtivaTrue(outro.getId())).hasSize(1);
    }

    @Test
    void falhaIntermediariaFazRollbackInclusiveDosDeletesAnteriores() throws IOException {
        Usuario titular = usuarioRepository.save(TestDataFactory.usuario("Rollback", "rollback-lgpd@teste.com", "hash"));
        montarGrafo(titular);
        Path arquivo = UPLOAD_DIR.resolve(titular.getId().toString()).resolve("comprovante.pdf");
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, "conteudo");

        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION impedir_delete_meta_teste() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'falha intermediaria de teste'; END;
            $$ LANGUAGE plpgsql
            """);
        jdbcTemplate.execute("CREATE TRIGGER impedir_delete_meta_teste BEFORE DELETE ON metas FOR EACH ROW EXECUTE FUNCTION impedir_delete_meta_teste() ");
        try {
            assertThatThrownBy(() -> usuarioExclusaoService.excluirConta(titular.getId()))
                    .isInstanceOf(Exception.class);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS impedir_delete_meta_teste ON metas");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS impedir_delete_meta_teste()");
        }

        assertThat(usuarioRepository.findById(titular.getId())).isPresent();
        assertThat(transacaoRepository.findByUsuarioId(titular.getId())).hasSize(2);
        assertThat(metaRepository.findByUsuarioId(titular.getId())).hasSize(1);
        assertThat(Files.exists(arquivo)).isTrue();

        // Não vaza o grafo preservado para os demais métodos desta IT sem @Transactional.
        usuarioExclusaoService.excluirConta(titular.getId());
    }

    /** Carteira, categoria, conta fixa recorrente com execuções nos 3 status, transação avulsa e meta. */
    private void montarGrafo(Usuario usuario) {
        Carteira carteira = carteiraRepository.save(TestDataFactory.carteira(usuario, "Principal", new BigDecimal("1000.00")));
        Categoria categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Moradia"));

        ContaFixa contaFixa = new ContaFixa();
        contaFixa.setUsuario(usuario);
        contaFixa.setCategoria(categoria);
        contaFixa.setCarteira(carteira);
        contaFixa.setNome("Aluguel");
        contaFixa.setValorPlanejado(new BigDecimal("900.00"));
        contaFixa.setDiaVencimento(10);
        contaFixa.setTipo(TipoTransacao.SAIDA);
        contaFixa.setStatus(StatusPagamento.PENDENTE);
        contaFixa.setExecucaoAutomatica(true);
        contaFixa = contaFixaRepository.save(contaFixa);

        Transacao daRecorrencia = transacaoRepository.save(
                TestDataFactory.transacao(usuario, categoria, "Aluguel (recorrência)", new BigDecimal("900.00")));
        transacaoRepository.save(
                TestDataFactory.transacao(usuario, categoria, "Mercado", new BigDecimal("120.00")));

        salvarExecucao(usuario, contaFixa, LocalDate.now().minusMonths(2), StatusExecucaoRecorrencia.REALIZADA, daRecorrencia, null);
        salvarExecucao(usuario, contaFixa, LocalDate.now().minusMonths(1), StatusExecucaoRecorrencia.PULADA, null, null);
        salvarExecucao(usuario, contaFixa, LocalDate.now(), StatusExecucaoRecorrencia.FALHA_SALDO, null, "Saldo insuficiente");

        Meta meta = new Meta();
        meta.setUsuario(usuario);
        meta.setNome("Reserva");
        meta.setValorTotal(new BigDecimal("5000.00"));
        meta.setValorReservado(new BigDecimal("100.00"));
        meta.setAtiva(true);
        metaRepository.save(meta);
    }

    private void salvarExecucao(Usuario usuario, ContaFixa contaFixa, LocalDate vencimento,
                                StatusExecucaoRecorrencia status, Transacao transacao, String mensagemFalha) {
        ExecucaoRecorrencia execucao = new ExecucaoRecorrencia();
        execucao.setUsuario(usuario);
        execucao.setContaFixa(contaFixa);
        execucao.setDataVencimento(vencimento);
        execucao.setStatus(status);
        execucao.setTentadoEm(LocalDateTime.now());
        execucao.setTransacao(transacao);
        execucao.setMensagemFalha(mensagemFalha);
        execucaoRepository.save(execucao);
    }

    private List<String> tabelasComColunaUsuarioId() {
        return jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'usuario_id'
                """, String.class);
    }
}
