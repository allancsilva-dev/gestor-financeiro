package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaPagamento;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaPagamentoRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.ContaService;
import com.gestor.financeiro.service.FaturaService;
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
 * PR-F2-08 — Pagamento de fatura como operacao unica: -caixa e -passivo
 * vinculados a mesma operacao PAGAMENTO_FATURA; cada pagamento parcial/total
 * gera registro proprio em fatura_pagamentos; idempotencia preservada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FaturaPagamentoOperacaoTest {

    @Autowired TransacaoService transacaoService;
    @Autowired ContaService contaService;
    @Autowired FaturaService faturaService;
    @Autowired ContaRepository contaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired FaturaCartaoRepository faturaCartaoRepository;
    @Autowired FaturaPagamentoRepository faturaPagamentoRepository;
    @Autowired MovimentoCarteiraRepository movimentoCarteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Conta cartao;
    private Carteira caixa;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Pagamento F2");
        usuario.setEmail("pagamento-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        caixa = new Carteira();
        caixa.setNome("Corrente");
        caixa.setTipo(TipoCarteira.CONTA_BANCARIA);
        caixa.setSaldo(new BigDecimal("1000.00"));
        caixa.setUsuario(usuario);
        caixa = carteiraRepository.save(caixa);

        Conta novo = new Conta();
        novo.setNome("Cartao");
        novo.setTipo(TipoConta.CREDITO);
        novo.setDiaFechamento(10);
        novo.setDiaVencimento(20);
        cartao = contaService.criar(novo, usuario.getId());

        Transacao compra = new Transacao();
        compra.setDescricao("Compra");
        compra.setValorTotal(new BigDecimal("200.00"));
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setData(LocalDate.of(2026, 7, 1));
        compra.setConta(cartao);
        compra.setParcelado(false);
        transacaoService.criar(compra, usuario.getId());
    }

    private Long faturaAbertaId() {
        return faturaCartaoRepository.findAll().stream()
                .filter(f -> f.getConta().getId().equals(cartao.getId()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void pagamentoParcialCriaOperacaoUnicaComCaixaEPassivoVinculados() {
        Long faturaId = faturaAbertaId();

        faturaService.pagarFatura(usuario.getId(), faturaId, new BigDecimal("80.00"),
                caixa.getId(), "pag-1");

        // registro proprio de pagamento
        List<FaturaPagamento> pagamentos =
                faturaPagamentoRepository.findByFaturaIdOrderByDataPagamentoAsc(faturaId);
        assertEquals(1, pagamentos.size());
        FaturaPagamento pagamento = pagamentos.get(0);
        assertEquals(0, new BigDecimal("80.00").compareTo(pagamento.getValor()));
        assertNotNull(pagamento.getOperacao());
        assertEquals(TipoOperacaoFinanceira.PAGAMENTO_FATURA, pagamento.getOperacao().getTipo());

        // mesma operacao vincula -caixa e -passivo
        List<MovimentoCarteira> movimentosDaOperacao = movimentoCarteiraRepository
                .findByOperacaoIdOrderByValorAssinadoAsc(pagamento.getOperacao().getId());
        assertEquals(2, movimentosDaOperacao.size());
        assertTrue(movimentosDaOperacao.stream()
                .allMatch(m -> m.getValorAssinado().signum() < 0));

        // caixa: 1000 - 80; passivo: 200 - 80 == valorGasto
        assertEquals(0, new BigDecimal("920.00").compareTo(
                carteiraRepository.findById(caixa.getId()).orElseThrow().getSaldo()));
        Conta atualizada = contaRepository.findById(cartao.getId()).orElseThrow();
        BigDecimal saldoPassivo = carteiraRepository
                .findById(atualizada.getContaFinanceira().getId()).orElseThrow().getSaldo();
        assertEquals(0, new BigDecimal("120.00").compareTo(saldoPassivo));
        assertEquals(0, saldoPassivo.compareTo(atualizada.getValorGasto()));
    }

    @Test
    void pagamentoTotalEmDuasParcelasGeraDoisRegistrosEQuitaFatura() {
        Long faturaId = faturaAbertaId();

        faturaService.pagarFatura(usuario.getId(), faturaId, new BigDecimal("150.00"),
                caixa.getId(), "pag-a");
        faturaService.pagarFatura(usuario.getId(), faturaId, new BigDecimal("50.00"),
                caixa.getId(), "pag-b");

        assertEquals(2, faturaPagamentoRepository
                .findByFaturaIdOrderByDataPagamentoAsc(faturaId).size());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                contaRepository.findById(cartao.getId()).orElseThrow().getValorGasto()));
        assertEquals(0, new BigDecimal("800.00").compareTo(
                carteiraRepository.findById(caixa.getId()).orElseThrow().getSaldo()));
    }

    @Test
    void retryComMesmaChaveNaoDuplicaPagamento() {
        Long faturaId = faturaAbertaId();

        faturaService.pagarFatura(usuario.getId(), faturaId, new BigDecimal("80.00"),
                caixa.getId(), "pag-idem");
        faturaService.pagarFatura(usuario.getId(), faturaId, new BigDecimal("80.00"),
                caixa.getId(), "pag-idem");

        assertEquals(1, faturaPagamentoRepository
                .findByFaturaIdOrderByDataPagamentoAsc(faturaId).size());
        assertEquals(0, new BigDecimal("920.00").compareTo(
                carteiraRepository.findById(caixa.getId()).orElseThrow().getSaldo()));
    }
}
