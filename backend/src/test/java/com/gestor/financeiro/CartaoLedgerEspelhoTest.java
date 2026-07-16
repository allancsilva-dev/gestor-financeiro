package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusOperacaoFinanceira;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.OperacaoFinanceiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-F2-07 — Cartao no ledger: compra gera operacao COMPRA_CARTAO compartilhada
 * entre lancamentos de fatura e movimento de passivo; exclusao gera estorno com
 * original ESTORNADA; invariante: saldo do passivo == faturas nao pagas em todo fluxo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartaoLedgerEspelhoTest {

    @Autowired TransacaoService transacaoService;
    @Autowired CartaoService cartaoService;
    @Autowired ContaRepository contaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired FaturaLancamentoRepository faturaLancamentoRepository;
    @Autowired OperacaoFinanceiraRepository operacaoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Conta cartao;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Espelho F2");
        usuario.setEmail("espelho-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        Conta novo = new Conta();
        novo.setNome("Cartao");
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        cartao = cartaoService.criar(novo, usuario.getId());
    }

    private Transacao compra(BigDecimal valor, int parcelas) {
        Transacao t = new Transacao();
        t.setDescricao("Compra teste");
        t.setValorTotal(valor);
        t.setTipo(TipoTransacao.SAIDA);
        t.setData(LocalDate.of(2026, 7, 1));
        t.setConta(cartao);
        t.setParcelado(parcelas > 1);
        t.setTotalParcelas(parcelas > 1 ? parcelas : null);
        return transacaoService.criar(t, usuario.getId());
    }

    private BigDecimal saldoPassivo() {
        return carteiraRepository.findById(
                contaRepository.findById(cartao.getId()).orElseThrow().getContaFinanceira().getId())
                .orElseThrow().getSaldo();
    }

    @Test
    void compraParceladaCriaOperacaoCompartilhadaEEspelhaPassivo() {
        Transacao t = compra(new BigDecimal("100.00"), 3);

        List<FaturaLancamento> lancamentos = faturaLancamentoRepository.findByTransacaoId(t.getId());
        assertEquals(3, lancamentos.size());

        // todos os lancamentos compartilham a mesma operacao COMPRA_CARTAO
        Long operacaoId = lancamentos.get(0).getOperacao().getId();
        assertNotNull(operacaoId);
        assertTrue(lancamentos.stream().allMatch(l -> operacaoId.equals(l.getOperacao().getId())));
        assertEquals(TipoOperacaoFinanceira.COMPRA_CARTAO,
                operacaoRepository.findById(operacaoId).orElseThrow().getTipo());

        // passivo espelhado no ledger == 100
        assertEquals(0, new BigDecimal("100.00").compareTo(saldoPassivo()));
    }

    @Test
    void exclusaoDeCompraGeraEstornoEZeraPassivo() {
        Transacao t = compra(new BigDecimal("80.00"), 1);
        Long operacaoCompraId = faturaLancamentoRepository.findByTransacaoId(t.getId())
                .get(0).getOperacao().getId();

        transacaoService.deletar(t.getId(), usuario.getId());

        // original estornada, conteudo intacto
        var original = operacaoRepository.findById(operacaoCompraId).orElseThrow();
        assertEquals(StatusOperacaoFinanceira.ESTORNADA, original.getStatus());

        // existe operacao ESTORNO referenciando a original
        assertTrue(operacaoRepository.findAll().stream().anyMatch(op ->
                op.getTipo() == TipoOperacaoFinanceira.ESTORNO
                        && op.getEstornoDe() != null
                        && op.getEstornoDe().getId().equals(operacaoCompraId)));

        // passivo zerado no ledger
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoPassivo()));
    }

    @Test
    void edicaoDeValorRessincronizaEspelho() {
        Transacao t = compra(new BigDecimal("60.00"), 1);

        Transacao editada = new Transacao();
        editada.setDescricao("Compra editada");
        editada.setValorTotal(new BigDecimal("90.00"));
        editada.setTipo(TipoTransacao.SAIDA);
        editada.setData(LocalDate.of(2026, 7, 2));
        editada.setConta(cartao);
        editada.setParcelado(false);
        transacaoService.atualizar(t.getId(), editada, usuario.getId());

        assertEquals(0, new BigDecimal("90.00").compareTo(saldoPassivo()));
    }
}
