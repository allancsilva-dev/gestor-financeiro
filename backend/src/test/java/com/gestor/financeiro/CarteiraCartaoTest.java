package com.gestor.financeiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.util.FaturaDatas;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/cartoes/carteira — a leitura que alimenta a tela Carteira.
 * Os dois testes que mais importam aqui não são de valor: são o de ausência de
 * efeito colateral (os GETs de FaturaService escrevem ao ler) e o de N+1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CarteiraCartaoTest {
    private static final String EMAIL = "alice-carteira@teste.com";
    private static final String EMAIL_BOB = "bob-carteira@teste.com";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ContaRepository contaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired FaturaCartaoRepository faturaRepository;
    @Autowired FaturaLancamentoRepository lancamentoRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Clock clock;

    private Usuario alice;

    @BeforeEach
    void setUp() {
        alice = usuarioRepository.save(TestDataFactory.usuario(
                "Alice Carteira", EMAIL, passwordEncoder.encode("123456")));
    }

    private Conta cartao(Usuario dono, String nome, BigDecimal limite, BigDecimal saldoPassivo,
                         int diaFechamento, int diaVencimento) {
        Carteira passivo = carteiraRepository.save(TestDataFactory.contaPassivaCartao(dono, nome));
        passivo.setSaldo(saldoPassivo);
        carteiraRepository.saveAndFlush(passivo);

        Conta cartao = TestDataFactory.cartao(dono, nome, passivo);
        cartao.setLimiteTotal(limite);
        cartao.setDiaFechamento(diaFechamento);
        cartao.setDiaVencimento(diaVencimento);
        return contaRepository.saveAndFlush(cartao);
    }

    private void lancamento(FaturaCartao fatura, String descricao, BigDecimal valor) {
        FaturaLancamento l = new FaturaLancamento();
        l.setFatura(fatura);
        l.setDescricao(descricao);
        l.setValor(valor);
        l.setDataCompra(fatura.getDataFechamento());
        l.setTipo(TipoFaturaLancamento.COMPRA);
        lancamentoRepository.saveAndFlush(l);
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/cartoes/carteira")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void naoVazaCartaoDeOutroUsuario() throws Exception {
        Usuario bob = usuarioRepository.save(TestDataFactory.usuario(
                "Bob", EMAIL_BOB, passwordEncoder.encode("123456")));
        cartao(alice, "Da Alice", new BigDecimal("1000.00"), BigDecimal.ZERO, 19, 27);
        cartao(bob, "Do Bob", new BigDecimal("9000.00"), BigDecimal.ZERO, 5, 12);

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Da Alice"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void calculaEmAbertoLimiteEPercentual() throws Exception {
        // Reproduz os números do mockup: 12.000 de limite, 3.368,38 em aberto.
        cartao(alice, "Nubank Ultravioleta", new BigDecimal("12000.00"),
                new BigDecimal("3368.38"), 19, 27);

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emAberto").value(3368.38))
                .andExpect(jsonPath("$[0].creditoAFavor").value(0))
                .andExpect(jsonPath("$[0].limiteDisponivel").value(8631.62))
                .andExpect(jsonPath("$[0].percentualUso").value(28));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void saldoCredorNaoViraEmAbertoNegativo() throws Exception {
        cartao(alice, "Com crédito", new BigDecimal("1000.00"), new BigDecimal("-120.00"), 19, 27);

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emAberto").value(0))
                .andExpect(jsonPath("$[0].creditoAFavor").value(120.00))
                .andExpect(jsonPath("$[0].percentualUso").value(0))
                .andExpect(jsonPath("$[0].limiteDisponivel").value(1120.00));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void limiteZeroNaoDividePorZero() throws Exception {
        cartao(alice, "Sem limite", BigDecimal.ZERO, new BigDecimal("50.00"), 19, 27);

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentualUso").value(0))
                .andExpect(jsonPath("$[0].emAberto").value(50.00));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void competenciaSemFaturaMaterializadaVemZeradaComDatas() throws Exception {
        Conta c = cartao(alice, "Novinho", new BigDecimal("1000.00"), BigDecimal.ZERO, 19, 27);
        long antes = faturaRepository.count();

        YearMonth atual = YearMonth.from(LocalDate.now(clock));
        String vencimentoEsperado = FaturaDatas.vencimento(c, atual).toString();

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                // janela padrão = anterior + atual + próxima
                .andExpect(jsonPath("$[0].faturas.length()").value(3))
                .andExpect(jsonPath("$[0].faturas[?(@.mes == %d && @.ano == %d)].id",
                        atual.getMonthValue(), atual.getYear()).value(org.hamcrest.Matchers.contains(
                                org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[0].dataVencimentoAtual").value(vencimentoEsperado));

        assertEquals(antes, faturaRepository.count(), "leitura da carteira não pode materializar fatura");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void faturaMaterializadaEntraComSaldoRestante() throws Exception {
        Conta c = cartao(alice, "Com fatura", new BigDecimal("5000.00"),
                new BigDecimal("300.00"), 19, 27);
        YearMonth atual = YearMonth.from(LocalDate.now(clock));

        FaturaCartao f = new FaturaCartao();
        f.setUsuario(alice);
        f.setConta(c);
        f.setMes(atual.getMonthValue());
        f.setAno(atual.getYear());
        f.setDataFechamento(FaturaDatas.fechamento(c, atual));
        f.setDataVencimento(FaturaDatas.vencimento(c, atual));
        f.setValorTotal(new BigDecimal("500.00"));
        f.setValorPago(new BigDecimal("200.00"));
        f.setStatus(FaturaStatus.ABERTA);
        faturaRepository.saveAndFlush(f);

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faturas[?(@.valorTotal == 500.00)].saldoRestante")
                        .value(org.hamcrest.Matchers.contains(300.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void totalVemDaSomaDosLancamentosNaoDoValorPersistido() throws Exception {
        // O valorTotal persistido e apenas fallback pre-V17: quem manda e a soma
        // dos lancamentos, que e o que pagarFatura valida. Sem isto a linha da
        // Carteira mostra um valor e o detalhe da fatura mostra outro.
        Conta c = cartao(alice, "Divergente", new BigDecimal("5000.00"),
                new BigDecimal("300.00"), 19, 27);
        YearMonth atual = YearMonth.from(LocalDate.now(clock));

        FaturaCartao f = new FaturaCartao();
        f.setUsuario(alice);
        f.setConta(c);
        f.setMes(atual.getMonthValue());
        f.setAno(atual.getYear());
        f.setDataFechamento(FaturaDatas.fechamento(c, atual));
        f.setDataVencimento(FaturaDatas.vencimento(c, atual));
        f.setValorTotal(new BigDecimal("999.99")); // persistido divergente de proposito
        f.setValorPago(BigDecimal.ZERO);
        f.setStatus(FaturaStatus.ABERTA);
        faturaRepository.saveAndFlush(f);

        lancamento(f, "Mercado", new BigDecimal("120.00"));
        lancamento(f, "Farmácia", new BigDecimal("30.50"));

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faturas[?(@.mes == %d)].valorTotal", atual.getMonthValue())
                        .value(org.hamcrest.Matchers.contains(150.50)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void faturaSemLancamentoCaiNoValorPersistido() throws Exception {
        Conta c = cartao(alice, "Legado", new BigDecimal("5000.00"), BigDecimal.ZERO, 19, 27);
        YearMonth atual = YearMonth.from(LocalDate.now(clock));

        FaturaCartao f = new FaturaCartao();
        f.setUsuario(alice);
        f.setConta(c);
        f.setMes(atual.getMonthValue());
        f.setAno(atual.getYear());
        f.setDataFechamento(FaturaDatas.fechamento(c, atual));
        f.setDataVencimento(FaturaDatas.vencimento(c, atual));
        f.setValorTotal(new BigDecimal("77.00"));
        f.setValorPago(BigDecimal.ZERO);
        f.setStatus(FaturaStatus.ABERTA);
        faturaRepository.saveAndFlush(f);

        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faturas[?(@.mes == %d)].valorTotal", atual.getMonthValue())
                        .value(org.hamcrest.Matchers.contains(77.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void faturaAtualVemPrimeiroNaLista() throws Exception {
        // A primeira linha e a que o usuario toca. Antes vinha a proxima fatura,
        // que quase nunca esta materializada — abria vazia e parecia bug.
        cartao(alice, "Ordem", new BigDecimal("1000.00"), BigDecimal.ZERO, 19, 27);
        YearMonth atual = YearMonth.from(LocalDate.now(clock));

        mockMvc.perform(get("/api/v1/cartoes/carteira?meses=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faturas[0].mes").value(atual.getMonthValue()))
                .andExpect(jsonPath("$[0].faturas[0].ano").value(atual.getYear()))
                .andExpect(jsonPath("$[0].faturas[1].mes").value(atual.plusMonths(1).getMonthValue()))
                .andExpect(jsonPath("$[0].faturas[2].mes").value(atual.minusMonths(1).getMonthValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void janelaDeMesesERespeitadaEClampada() throws Exception {
        cartao(alice, "Janela", new BigDecimal("1000.00"), BigDecimal.ZERO, 19, 27);

        mockMvc.perform(get("/api/v1/cartoes/carteira?meses=1"))
                .andExpect(jsonPath("$[0].faturas.length()").value(1));
        mockMvc.perform(get("/api/v1/cartoes/carteira?meses=99"))
                .andExpect(jsonPath("$[0].faturas.length()").value(12));
        mockMvc.perform(get("/api/v1/cartoes/carteira?meses=0"))
                .andExpect(jsonPath("$[0].faturas.length()").value(1));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void semCartoesDevolveListaVazia() throws Exception {
        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void naoEscreveNoBanco() throws Exception {
        Conta c = cartao(alice, "Somente leitura", new BigDecimal("1000.00"),
                new BigDecimal("100.00"), 19, 27);
        long faturasAntes = faturaRepository.count();
        BigDecimal saldoAntes = c.getContaFinanceira().getSaldo();

        mockMvc.perform(get("/api/v1/cartoes/carteira")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cartoes/carteira")).andExpect(status().isOk());

        assertEquals(faturasAntes, faturaRepository.count());
        assertEquals(0, saldoAntes.compareTo(
                carteiraRepository.findById(c.getContaFinanceira().getId()).orElseThrow().getSaldo()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void numeroDeQueriesNaoCresceComQuantidadeDeCartoes() throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        cartao(alice, "Um", new BigDecimal("1000.00"), BigDecimal.ZERO, 19, 27);
        stats.clear();
        mockMvc.perform(get("/api/v1/cartoes/carteira")).andExpect(status().isOk());
        long comUm = stats.getPrepareStatementCount();

        for (int i = 2; i <= 6; i++) {
            cartao(alice, "Cartao " + i, new BigDecimal("1000.00"), BigDecimal.ZERO, 19, 27);
        }
        stats.clear();
        mockMvc.perform(get("/api/v1/cartoes/carteira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
        long comSeis = stats.getPrepareStatementCount();

        assertEquals(comUm, comSeis,
                "carteira deve custar o mesmo numero de queries com 1 ou 6 cartoes (N+1): "
                        + comUm + " -> " + comSeis);
        assertTrue(comSeis <= 5, "carteira nao deve passar de 5 queries, foi " + comSeis);
    }
}
