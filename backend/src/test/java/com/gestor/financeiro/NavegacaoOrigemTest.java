package com.gestor.financeiro;

import com.gestor.financeiro.dto.AtivoRequest;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.InvestimentoService;
import com.gestor.financeiro.service.MetricasService;
import com.gestor.financeiro.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PR-F3-04 — Navegacao fornecida pelo backend na Origem: destino, ID e
 * filtros. Origem sem destino exato permanece informativa (navegacao nula).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NavegacaoOrigemTest {

    @Autowired MetricasService metricasService;
    @Autowired TransacaoService transacaoService;
    @Autowired CartaoService cartaoService;
    @Autowired InvestimentoService investimentoService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired TransacaoRepository transacaoRepository;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired FaturaCartaoRepository faturaCartaoRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Carteira corrente;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Navegacao F3", "navegacao-f3@teste.com", "x"));
        corrente = carteiraRepository.save(TestDataFactory.carteira(
                usuario, "Corrente", new BigDecimal("1000.00")));
    }

    @Test
    void contaFinanceiraNavegaParaExtratoDaConta() {
        List<MetricasService.Origem> origens =
                metricasService.origens(usuario.getId(), "DISPONIVEL_AGORA");

        assertEquals(1, origens.size());
        MetricasService.Navegacao nav = origens.get(0).navegacao();
        assertEquals(MetricasService.Navegacao.EXTRATO_CONTA, nav.destino());
        assertEquals(corrente.getId(), nav.id());
        assertNull(nav.filtros());
    }

    @Test
    void faturaNavegaParaFaturaEParcelaNavegaParaTransacao() {
        // Fatura aberta vencendo hoje (dentro do horizonte default)
        Conta cartao = new Conta();
        cartao.setNome("Cartao");
        cartao.setDiaFechamento(28);
        cartao.setDiaVencimento(10);
        cartao = cartaoService.criar(cartao, usuario.getId());
        FaturaCartao fatura = new FaturaCartao();
        fatura.setUsuario(usuario);
        fatura.setConta(cartao);
        fatura.setMes(hoje.getMonthValue());
        fatura.setAno(hoje.getYear());
        fatura.setDataFechamento(hoje.minusDays(1));
        fatura.setDataVencimento(hoje);
        fatura.setValorTotal(new BigDecimal("400.00"));
        fatura.setValorPago(BigDecimal.ZERO);
        fatura = faturaCartaoRepository.save(fatura);

        // Parcela pendente vencendo hoje, de transacao parcelada fora do cartao
        Transacao parcelada = new Transacao();
        parcelada.setUsuario(usuario);
        parcelada.setDescricao("Curso");
        parcelada.setValorTotal(new BigDecimal("200.00"));
        parcelada.setTipo(TipoTransacao.SAIDA);
        parcelada.setData(hoje.minusMonths(1));
        parcelada.setCarteira(corrente);
        parcelada.setParcelado(true);
        parcelada.setTotalParcelas(2);
        parcelada = transacaoRepository.save(parcelada);
        Parcela parcela = new Parcela();
        parcela.setTransacao(parcelada);
        parcela.setNumeroParcela(1);
        parcela.setTotalParcelas(2);
        parcela.setValor(new BigDecimal("100.00"));
        parcela.setDataVencimento(hoje);
        parcela.setStatus(StatusPagamento.PENDENTE);
        parcelaRepository.save(parcela);

        List<MetricasService.Origem> origens =
                metricasService.origens(usuario.getId(), "COMPROMETIDO");

        MetricasService.Origem origemFatura = origens.stream()
                .filter(o -> "FATURA".equals(o.tipo())).findFirst().orElseThrow();
        assertEquals(MetricasService.Navegacao.FATURA, origemFatura.navegacao().destino());
        assertEquals(fatura.getId(), origemFatura.navegacao().id());

        MetricasService.Origem origemParcela = origens.stream()
                .filter(o -> "PARCELA".equals(o.tipo())).findFirst().orElseThrow();
        assertEquals(MetricasService.Navegacao.TRANSACAO, origemParcela.navegacao().destino());
        assertEquals(parcelada.getId(), origemParcela.navegacao().id());
    }

    @Test
    void posicaoNavegaParaInvestimento() {
        AtivoRequest ativo = new AtivoRequest();
        ativo.setTicker("NEXO3");
        ativo.setNome("Nexos");
        ativo.setTipo("ACAO");
        ativo.setValorAtual(new BigDecimal("35.00"));
        Long ativoId = investimentoService.criarAtivo(usuario.getId(), ativo).getId();
        com.gestor.financeiro.dto.MovimentacaoRequest compra =
                new com.gestor.financeiro.dto.MovimentacaoRequest();
        compra.setTipo("COMPRA");
        compra.setData(hoje);
        compra.setQuantidade(BigDecimal.TEN);
        compra.setPrecoUnitario(new BigDecimal("30.00"));
        compra.setCarteiraId(corrente.getId());
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra);

        List<MetricasService.Origem> origens =
                metricasService.origens(usuario.getId(), "INVESTIDO");

        assertEquals(1, origens.size());
        assertEquals(MetricasService.Navegacao.INVESTIMENTO, origens.get(0).navegacao().destino());
        assertEquals(ativoId, origens.get(0).navegacao().id());
    }

    @Test
    void entradasCompetenciaNavegaParaTransacoesFiltradasEDemaisFicamInformativas() {
        Transacao entrada = new Transacao();
        entrada.setDescricao("Salario");
        entrada.setValorTotal(new BigDecimal("2000.00"));
        entrada.setTipo(TipoTransacao.ENTRADA);
        entrada.setData(hoje);
        entrada.setCarteira(corrente);
        entrada.setParcelado(false);
        transacaoService.criar(entrada, usuario.getId());

        List<MetricasService.Origem> origens =
                metricasService.origens(usuario.getId(), "RESULTADO_MENSAL");

        MetricasService.Origem entradas = origens.stream()
                .filter(o -> "ENTRADAS_COMPETENCIA".equals(o.tipo())).findFirst().orElseThrow();
        MetricasService.Navegacao nav = entradas.navegacao();
        assertEquals(MetricasService.Navegacao.TRANSACOES, nav.destino());
        assertNull(nav.id());
        assertEquals(hoje.withDayOfMonth(1).toString(), nav.filtros().get("inicio"));
        assertEquals(hoje.withDayOfMonth(hoje.lengthOfMonth()).toString(), nav.filtros().get("fim"));
        assertEquals("ENTRADA", nav.filtros().get("tipo"));

        // Sem filtro exato disponivel: informativa, sem link aproximado
        origens.stream()
                .filter(o -> !"ENTRADAS_COMPETENCIA".equals(o.tipo()))
                .forEach(o -> assertNull(o.navegacao()));
    }
}
