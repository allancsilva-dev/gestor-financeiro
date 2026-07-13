package com.gestor.financeiro;

import com.gestor.financeiro.dto.FaturaResponse;
import com.gestor.financeiro.model.*;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.*;
import com.gestor.financeiro.service.FaturaService;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.service.CronogramaService;
import com.gestor.financeiro.service.ProjecaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FaturaCartaoWorkflowTest {

    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private FaturaService faturaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;

    @Autowired
    private FaturaLancamentoRepository faturaLancamentoRepository;

    @Autowired private ParcelaRepository parcelaRepository;
    @Autowired private CronogramaService cronogramaService;
    @Autowired private ProjecaoService projecaoService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria categoria;
    private Conta cartao;
    private Carteira carteira;

    @BeforeEach
    void setup() {
        faturaLancamentoRepository.deleteAll();
        faturaCartaoRepository.deleteAll();
        movimentoCarteiraRepository.deleteAll();
        transacaoRepository.deleteAll();
        carteiraRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = usuarioRepository.save(
                TestDataFactory.usuario("Fatura", "fatura-workflow@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));

        cartao = TestDataFactory.conta(usuario, "Cartão Roxo", TipoConta.CREDITO);
        cartao.setDiaFechamento(28);
        cartao.setDiaVencimento(5);
        cartao = contaRepository.save(cartao);

        carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome("Conta corrente");
        carteira.setTipo(TipoCarteira.CONTA_BANCARIA);
        carteira.setSaldo(new BigDecimal("1000.00"));
        carteira = carteiraRepository.save(carteira);
    }

    @Test
    void compraParceladaNoCartaoCriaLancamentosNasFaturasCorretasSemDebitarCarteira() {
        Transacao compra = compraCartao("Notebook", new BigDecimal("120.00"), LocalDate.of(2026, 7, 29));
        compra.setCarteira(carteira);
        compra.setParcelado(true);
        compra.setTotalParcelas(3);

        transacaoService.criar(compra, usuario.getId());

        assertEquals(0, parcelaRepository.findByTransacaoId(compra.getId()).size());
        assertEquals(3, cronogramaService.listar(compra.getId(), usuario.getId()).size());

        assertEquals(0, new BigDecimal("1000.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("120.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        FaturaCartao setembro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 9, 2026).orElseThrow();
        FaturaCartao outubro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 10, 2026).orElseThrow();

        assertEquals(0, new BigDecimal("40.00").compareTo(agosto.getValorTotal()));
        assertEquals(0, new BigDecimal("40.00").compareTo(setembro.getValorTotal()));
        assertEquals(0, new BigDecimal("40.00").compareTo(outubro.getValorTotal()));

        FaturaResponse response = faturaService.buscarPorMes(usuario.getId(), cartao.getId(), 8, 2026);
        assertEquals(1, response.lancamentos().size());
        assertEquals(1, response.lancamentos().get(0).parcelaAtual());
        assertEquals(3, response.lancamentos().get(0).totalParcelas());
        assertEquals(0, new BigDecimal("40.00").compareTo(response.valorTotal()));
    }

    @Test
    void projecaoUsaSomenteSaldoRestanteDaFaturaSemParcelaDuplicada() {
        Transacao compra = compraCartao("Notebook", new BigDecimal("120.00"), LocalDate.of(2026, 7, 29));
        compra.setParcelado(true);
        compra.setTotalParcelas(3);
        transacaoService.criar(compra, usuario.getId());

        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        agosto.setValorPago(new BigDecimal("10.00"));
        faturaCartaoRepository.save(agosto);

        var vencimentoProjetado = projecaoService.projetar(usuario.getId(), 3).meses().stream()
                .filter(m -> m.mes() == 9 && m.ano() == 2026).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("30.00").compareTo(vencimentoProjetado.totalFaturas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vencimentoProjetado.totalParcelas()));
    }

    @Test
    void parcelamentoForaDoCartaoContinuaEmParcelaComRestoNaUltima() {
        Conta debito = contaRepository.save(TestDataFactory.conta(usuario, "Débito", TipoConta.DEBITO));
        Transacao compra = TestDataFactory.transacao(usuario, categoria, "Curso", new BigDecimal("100.00"));
        compra.setConta(debito);
        compra.setParcelado(true);
        compra.setTotalParcelas(3);
        transacaoService.criar(compra, usuario.getId());

        List<Parcela> parcelas = parcelaRepository.findByTransacaoId(compra.getId());
        assertEquals(3, parcelas.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(
                parcelas.stream().map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, new BigDecimal("33.34").compareTo(parcelas.get(2).getValor()));
    }

    @Test
    void pagarFaturaDebitaCarteiraLiberaLimiteEMarcaComoPaga() {
        transacaoService.criar(
                compraCartao("Mercado", new BigDecimal("100.00"), LocalDate.of(2026, 7, 10)),
                usuario.getId());

        FaturaCartao fatura = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();

        FaturaResponse response = faturaService.pagarFatura(
                usuario.getId(), fatura.getId(), new BigDecimal("100.00"), carteira.getId());

        assertEquals(FaturaStatus.PAGA.name(), response.status());
        assertEquals(0, new BigDecimal("900.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        List<MovimentoCarteira> movimentos = movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId());
        assertEquals(1, movimentos.size());
        assertEquals(OrigemMovimentoCarteira.FATURA_CARTAO, movimentos.get(0).getOrigem());
        assertEquals(0, new BigDecimal("-100.00").compareTo(movimentos.get(0).getValorAssinado()));
    }

    @Test
    void pagarFaturaPermitePagamentoParcialEApenasQuitaSaldoRestante() {
        transacaoService.criar(
                compraCartao("Mercado", new BigDecimal("100.00"), LocalDate.of(2026, 7, 10)),
                usuario.getId());

        FaturaCartao fatura = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();

        FaturaResponse parcial = faturaService.pagarFatura(
                usuario.getId(), fatura.getId(), new BigDecimal("40.00"), carteira.getId(), "parcial-1");

        assertEquals(FaturaStatus.ABERTA.name(), parcial.status());
        assertEquals(0, new BigDecimal("40.00").compareTo(parcial.valorPago()));
        assertEquals(0, new BigDecimal("960.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(0, new BigDecimal("60.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        FaturaResponse quitada = faturaService.pagarFatura(
                usuario.getId(), fatura.getId(), new BigDecimal("60.00"), carteira.getId(), "parcial-2");

        assertEquals(FaturaStatus.PAGA.name(), quitada.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(quitada.valorPago()));
        assertEquals(0, new BigDecimal("900.00").compareTo(
                carteiraRepository.findById(carteira.getId()).orElseThrow().getSaldo()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));

        FaturaResponse retry = faturaService.pagarFatura(
                usuario.getId(), fatura.getId(), new BigDecimal("60.00"), carteira.getId(), "parcial-2");

        assertEquals(FaturaStatus.PAGA.name(), retry.status());
        assertEquals(2, movimentoCarteiraRepository
                .findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(usuario.getId(), carteira.getId())
                .size());
    }

    @Test
    void ultimaParcelaAbsorveArredondamentoELimiteZeraAposPagarTodasAsFaturas() {
        Transacao compra = compraCartao("Celular", new BigDecimal("100.00"), LocalDate.of(2026, 7, 29));
        compra.setParcelado(true);
        compra.setTotalParcelas(3);

        transacaoService.criar(compra, usuario.getId());

        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        FaturaCartao setembro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 9, 2026).orElseThrow();
        FaturaCartao outubro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 10, 2026).orElseThrow();

        assertEquals(0, new BigDecimal("33.33").compareTo(agosto.getValorTotal()));
        assertEquals(0, new BigDecimal("33.33").compareTo(setembro.getValorTotal()));
        assertEquals(0, new BigDecimal("33.34").compareTo(outubro.getValorTotal()));

        faturaService.pagarFatura(usuario.getId(), agosto.getId(), new BigDecimal("33.33"), carteira.getId());
        faturaService.pagarFatura(usuario.getId(), setembro.getId(), new BigDecimal("33.33"), carteira.getId());
        faturaService.pagarFatura(usuario.getId(), outubro.getId(), new BigDecimal("33.34"), carteira.getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
    }

    @Test
    void editarValorDeCompraNoCartaoRessincronizaFaturaELimite() {
        Transacao compra = transacaoService.criar(
                compraCartao("Mercado", new BigDecimal("100.00"), LocalDate.of(2026, 7, 10)),
                usuario.getId());

        Transacao atualizacao = new Transacao();
        atualizacao.setDescricao("Mercado ajustado");
        atualizacao.setValorTotal(new BigDecimal("150.00"));
        atualizacao.setData(LocalDate.of(2026, 7, 10));

        transacaoService.atualizar(compra.getId(), atualizacao, usuario.getId());

        FaturaCartao julho = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(julho.getValorTotal()));
        assertEquals(0, new BigDecimal("150.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
    }

    @Test
    void compraRetroativaNaoEntraEmFaturaPagaVaiParaProximaAberta() {
        transacaoService.criar(
                compraCartao("Mercado", new BigDecimal("100.00"), LocalDate.of(2026, 7, 10)),
                usuario.getId());

        FaturaCartao julho = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        faturaService.pagarFatura(usuario.getId(), julho.getId(), new BigDecimal("100.00"), carteira.getId());

        transacaoService.criar(
                compraCartao("Farmácia retroativa", new BigDecimal("50.00"), LocalDate.of(2026, 7, 15)),
                usuario.getId());

        FaturaCartao julhoAposCompra = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(julhoAposCompra.getValorTotal()));

        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        assertEquals(0, new BigDecimal("50.00").compareTo(agosto.getValorTotal()));
    }

    @Test
    void editarCompraJaPagaGeraLancamentoDeAjusteNaProximaFatura() {
        Transacao compra = transacaoService.criar(
                compraCartao("Mercado", new BigDecimal("100.00"), LocalDate.of(2026, 7, 10)),
                usuario.getId());

        FaturaCartao julho = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        faturaService.pagarFatura(usuario.getId(), julho.getId(), new BigDecimal("100.00"), carteira.getId());

        Transacao atualizacao = new Transacao();
        atualizacao.setDescricao("Mercado ajustado");
        atualizacao.setValorTotal(new BigDecimal("150.00"));
        atualizacao.setData(LocalDate.of(2026, 7, 10));

        transacaoService.atualizar(compra.getId(), atualizacao, usuario.getId());

        // Fatura paga permanece imutável
        FaturaCartao julhoDepois = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(julhoDepois.getValorTotal()));

        // Diferença entra como ajuste na próxima fatura aberta
        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        assertEquals(0, new BigDecimal("50.00").compareTo(agosto.getValorTotal()));

        FaturaLancamento ajuste = faturaLancamentoRepository.findByTransacaoId(compra.getId()).stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.AJUSTE)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("50.00").compareTo(ajuste.getValor()));

        assertEquals(0, new BigDecimal("50.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
    }

    @Test
    void editarCompraParceladaRecalculaParcelaCheiaENaoDivideRestantePorAbertas() {
        Transacao compra = compraCartao("Notebook", new BigDecimal("300.00"), LocalDate.of(2026, 7, 10));
        compra.setParcelado(true);
        compra.setTotalParcelas(3);
        Transacao salva = transacaoService.criar(compra, usuario.getId());

        FaturaCartao julho = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        faturaService.pagarFatura(usuario.getId(), julho.getId(), new BigDecimal("100.00"), carteira.getId());

        Transacao atualizacao = new Transacao();
        atualizacao.setDescricao("Notebook ajustado");
        atualizacao.setValorTotal(new BigDecimal("600.00"));
        atualizacao.setData(LocalDate.of(2026, 7, 10));

        transacaoService.atualizar(salva.getId(), atualizacao, usuario.getId());

        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        FaturaCartao setembro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 9, 2026).orElseThrow();

        assertEquals(0, new BigDecimal("300.00").compareTo(agosto.getValorTotal()));
        assertEquals(0, new BigDecimal("200.00").compareTo(setembro.getValorTotal()));

        List<FaturaLancamento> lancamentos = faturaLancamentoRepository.findByTransacaoId(salva.getId());
        FaturaLancamento segundaParcela = lancamentos.stream()
                .filter(l -> Integer.valueOf(2).equals(l.getParcelaNumero()))
                .findFirst().orElseThrow();
        FaturaLancamento terceiraParcela = lancamentos.stream()
                .filter(l -> Integer.valueOf(3).equals(l.getParcelaNumero()))
                .findFirst().orElseThrow();
        FaturaLancamento ajuste = lancamentos.stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.AJUSTE)
                .findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("200.00").compareTo(segundaParcela.getValor()));
        assertEquals(0, new BigDecimal("200.00").compareTo(terceiraParcela.getValor()));
        assertEquals(0, new BigDecimal("100.00").compareTo(ajuste.getValor()));
    }

    @Test
    void cancelarCompraParceladaComFaturaPagaGeraEstornoNaProximaFatura() {
        Transacao compra = compraCartao("Notebook", new BigDecimal("300.00"), LocalDate.of(2026, 7, 10));
        compra.setParcelado(true);
        compra.setTotalParcelas(3);
        Transacao salva = transacaoService.criar(compra, usuario.getId());

        FaturaCartao julho = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 7, 2026).orElseThrow();
        faturaService.pagarFatura(usuario.getId(), julho.getId(), new BigDecimal("100.00"), carteira.getId());

        transacaoService.deletar(salva.getId(), usuario.getId());

        // Parcelas em faturas abertas removidas; parte paga vira estorno (crédito) na próxima fatura
        FaturaCartao agosto = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 8, 2026).orElseThrow();
        assertEquals(0, new BigDecimal("-100.00").compareTo(agosto.getValorTotal()));

        FaturaCartao setembro = faturaCartaoRepository
                .findByContaIdAndMesAndAno(cartao.getId(), 9, 2026).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(setembro.getValorTotal()));

        FaturaLancamento estorno = faturaLancamentoRepository.findByTransacaoId(salva.getId()).stream()
                .filter(l -> l.getTipo() == TipoFaturaLancamento.ESTORNO)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("-100.00").compareTo(estorno.getValor()));

        // Crédito de limite: -100 compensa compras futuras na fatura de agosto
        assertEquals(0, new BigDecimal("-100.00").compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
    }

    private Transacao compraCartao(String descricao, BigDecimal valor, LocalDate data) {
        Transacao transacao = new Transacao();
        transacao.setCategoria(categoria);
        transacao.setConta(cartao);
        transacao.setDescricao(descricao);
        transacao.setValorTotal(valor);
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setData(data);
        transacao.setParcelado(false);
        transacao.setRecorrente(false);
        return transacao;
    }
}
