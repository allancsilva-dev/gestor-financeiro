package com.gestor.financeiro;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.TransacaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillResult;
import com.gestor.financeiro.service.ParcelamentoRoundingBackfillService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ParcelamentoRoundingBackfillServiceTest {

    @Autowired
    private ParcelamentoRoundingBackfillService backfillService;
    @Autowired
    private FaturaLancamentoRepository faturaLancamentoRepository;
    @Autowired
    private FaturaCartaoRepository faturaCartaoRepository;
    @Autowired
    private ParcelaRepository parcelaRepository;
    @Autowired
    private TransacaoRepository transacaoRepository;
    @Autowired
    private ContaRepository contaRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Categoria categoria;
    private Conta credito;

    @BeforeEach
    void setup() {
        cleanup();
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Backfill", "rounding-backfill@teste.com", passwordEncoder.encode("123456")));
        categoria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Cartao"));
        credito = contaRepository.save(TestDataFactory.conta(usuario, "Cartao", TipoConta.CREDITO));
    }

    @AfterEach
    void cleanup() {
        faturaLancamentoRepository.deleteAll();
        faturaCartaoRepository.deleteAll();
        parcelaRepository.deleteAll();
        transacaoRepository.deleteAll();
        contaRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void diagnosticaCorrigeParcelasELancamentosLegadosEPermaneceIdempotente() {
        Transacao compra = compraParcelada("Compra antiga", new BigDecimal("100.00"));
        criarParcelasLegadas(compra, new BigDecimal("33.33"));
        FaturaCartao fatura = criarFatura(FaturaStatus.ABERTA, new BigDecimal("99.99"));
        criarLancamentosLegados(fatura, compra, new BigDecimal("33.33"));
        credito.setValorGasto(new BigDecimal("99.99"));
        contaRepository.save(credito);

        ParcelamentoRoundingBackfillResult diagnostico = backfillService.diagnosticarUsuario(usuario.getId());
        assertEquals(true, diagnostico.dryRun());
        assertEquals(1, diagnostico.transacoesComResiduoEmParcelas());
        assertEquals(1, diagnostico.transacoesComResiduoEmFaturas());
        assertEquals(0, diagnostico.parcelasCorrigidas());
        assertEquals(0, diagnostico.lancamentosCorrigidos());

        ParcelamentoRoundingBackfillResult aplicado = backfillService.corrigirUsuario(usuario.getId());
        assertEquals(false, aplicado.dryRun());
        assertEquals(1, aplicado.parcelasCorrigidas());
        assertEquals(1, aplicado.lancamentosCorrigidos());

        assertBig(new BigDecimal("100.00"), parcelaRepository.somarValorByTransacaoId(compra.getId()));
        assertBig(new BigDecimal("100.00"), faturaLancamentoRepository.somarValorByTransacaoIdAndTipo(
                compra.getId(), TipoFaturaLancamento.COMPRA));
        assertBig(new BigDecimal("33.34"), parcelaRepository
                .findTopByTransacaoIdOrderByNumeroParcelaDescIdDesc(compra.getId()).orElseThrow().getValor());
        assertBig(new BigDecimal("33.34"), faturaLancamentoRepository
                .findTopByTransacaoIdAndTipoOrderByParcelaNumeroDescIdDesc(
                        compra.getId(), TipoFaturaLancamento.COMPRA).orElseThrow().getValor());
        assertBig(new BigDecimal("100.00"), faturaCartaoRepository.findById(fatura.getId()).orElseThrow().getValorTotal());
        assertBig(new BigDecimal("100.00"), contaRepository.findById(credito.getId()).orElseThrow().getValorGasto());

        ParcelamentoRoundingBackfillResult segunda = backfillService.corrigirUsuario(usuario.getId());
        assertEquals(0, segunda.transacoesComResiduoEmParcelas());
        assertEquals(0, segunda.transacoesComResiduoEmFaturas());
        assertEquals(0, segunda.parcelasCorrigidas());
        assertEquals(0, segunda.lancamentosCorrigidos());
    }

    @Test
    void faturaPagaCorrigeTotalHistoricoSemAlterarLimiteAtual() {
        Transacao compra = compraParcelada("Compra paga", new BigDecimal("100.00"));
        FaturaCartao fatura = criarFatura(FaturaStatus.PAGA, new BigDecimal("99.99"));
        criarLancamentosLegados(fatura, compra, new BigDecimal("33.33"));
        credito.setValorGasto(BigDecimal.ZERO);
        contaRepository.save(credito);

        backfillService.corrigirUsuario(usuario.getId());

        assertBig(new BigDecimal("100.00"), faturaCartaoRepository.findById(fatura.getId()).orElseThrow().getValorTotal());
        assertBig(BigDecimal.ZERO, contaRepository.findById(credito.getId()).orElseThrow().getValorGasto());
    }

    @Test
    void lancamentoComAjusteNaoEntraNaCorrecaoAutomaticaDeFatura() {
        Transacao compra = compraParcelada("Compra editada", new BigDecimal("100.00"));
        FaturaCartao fatura = criarFatura(FaturaStatus.ABERTA, new BigDecimal("99.99"));
        criarLancamentosLegados(fatura, compra, new BigDecimal("33.33"));
        criarLancamento(fatura, compra, "Ajuste", new BigDecimal("0.01"), null, TipoFaturaLancamento.AJUSTE);

        ParcelamentoRoundingBackfillResult diagnostico = backfillService.diagnosticarUsuario(usuario.getId());

        assertEquals(0, diagnostico.transacoesComResiduoEmFaturas());
        assertEquals(0, diagnostico.lancamentosCorrigidos());
    }

    private Transacao compraParcelada(String descricao, BigDecimal valor) {
        Transacao compra = TestDataFactory.transacao(usuario, categoria, descricao, valor);
        compra.setConta(credito);
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setParcelado(true);
        compra.setTotalParcelas(3);
        compra.setValorParcela(new BigDecimal("33.33"));
        compra.setAtiva(true);
        return transacaoRepository.save(compra);
    }

    private void criarParcelasLegadas(Transacao compra, BigDecimal valor) {
        for (int i = 1; i <= 3; i++) {
            Parcela parcela = new Parcela();
            parcela.setTransacao(compra);
            parcela.setNumeroParcela(i);
            parcela.setTotalParcelas(3);
            parcela.setValor(valor);
            parcela.setDataVencimento(LocalDate.now().plusMonths(i));
            parcela.setStatus(StatusPagamento.PENDENTE);
            parcelaRepository.save(parcela);
        }
    }

    private FaturaCartao criarFatura(FaturaStatus status, BigDecimal valorTotal) {
        FaturaCartao fatura = new FaturaCartao();
        fatura.setUsuario(usuario);
        fatura.setConta(credito);
        fatura.setMes(1);
        fatura.setAno(2026);
        fatura.setValorTotal(valorTotal);
        fatura.setValorPago(status == FaturaStatus.PAGA ? valorTotal : BigDecimal.ZERO);
        fatura.setStatus(status);
        return faturaCartaoRepository.save(fatura);
    }

    private void criarLancamentosLegados(FaturaCartao fatura, Transacao compra, BigDecimal valor) {
        for (int i = 1; i <= 3; i++) {
            criarLancamento(fatura, compra, compra.getDescricao(), valor, i, TipoFaturaLancamento.COMPRA);
        }
    }

    private void criarLancamento(FaturaCartao fatura, Transacao compra, String descricao, BigDecimal valor,
                                 Integer parcelaNumero, TipoFaturaLancamento tipo) {
        FaturaLancamento lancamento = new FaturaLancamento();
        lancamento.setFatura(fatura);
        lancamento.setTransacao(compra);
        lancamento.setDescricao(descricao);
        lancamento.setValor(valor);
        lancamento.setDataCompra(compra.getData());
        lancamento.setParcelaNumero(parcelaNumero);
        lancamento.setTotalParcelas(parcelaNumero == null ? null : 3);
        lancamento.setTipo(tipo);
        faturaLancamentoRepository.save(lancamento);
    }

    private static void assertBig(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
