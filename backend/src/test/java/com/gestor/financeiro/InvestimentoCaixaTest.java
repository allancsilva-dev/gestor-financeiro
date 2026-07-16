package com.gestor.financeiro;

import com.gestor.financeiro.dto.AtivoRequest;
import com.gestor.financeiro.dto.AtivoResponse;
import com.gestor.financeiro.dto.MovimentacaoRequest;
import com.gestor.financeiro.dto.MovimentacaoResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentacaoAtivo;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ConciliacaoInvestimento;
import com.gestor.financeiro.model.enums.TipoCarteira;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentacaoAtivoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InvestimentoService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-13 — Investimento ligado ao caixa (ADR-0011): operacao real exige
 * conta de caixa (conversao patrimonial, nunca despesa); snapshot EXTERNO e
 * explicito e nao movimenta caixa.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvestimentoCaixaTest {

    @Autowired InvestimentoService investimentoService;
    @Autowired MovimentacaoAtivoRepository movimentacaoRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Carteira caixa;
    private Long ativoId;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Invest F2");
        usuario.setEmail("invest-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        caixa = new Carteira();
        caixa.setNome("Corrente");
        caixa.setTipo(TipoCarteira.CONTA_BANCARIA);
        caixa.setSaldo(new BigDecimal("2000.00"));
        caixa.setUsuario(usuario);
        caixa = carteiraRepository.save(caixa);

        AtivoRequest ativo = new AtivoRequest();
        ativo.setTicker("NEXO11");
        ativo.setNome("Nexos FII");
        ativo.setTipo("FII");
        AtivoResponse criado = investimentoService.criarAtivo(usuario.getId(), ativo);
        ativoId = criado.getId();
    }

    private MovimentacaoRequest compra(BigDecimal qtd, BigDecimal preco, Long carteiraId, Boolean externa) {
        MovimentacaoRequest r = new MovimentacaoRequest();
        r.setTipo("COMPRA");
        r.setData(LocalDate.of(2026, 7, 10));
        r.setQuantidade(qtd);
        r.setPrecoUnitario(preco);
        r.setCarteiraId(carteiraId);
        r.setExterna(externa);
        return r;
    }

    @Test
    void compraSemCaixaESemFlagExternaERejeitada() {
        assertThrows(BusinessException.class, () -> investimentoService.adicionarMovimentacao(
                usuario.getId(), ativoId, compra(BigDecimal.TEN, new BigDecimal("10.00"), null, null)));
    }

    @Test
    void compraComCaixaEConversaoPatrimonialConciliada() {
        MovimentacaoResponse response = investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                compra(BigDecimal.TEN, new BigDecimal("10.00"), caixa.getId(), null));

        assertEquals(ConciliacaoInvestimento.CONCILIADA, response.getConciliacao());
        assertNotNull(response.getOperacaoId());

        // caixa debitado (conversao patrimonial)
        assertEquals(0, new BigDecimal("1900.00").compareTo(
                carteiraRepository.findById(caixa.getId()).orElseThrow().getSaldo()));

        List<MovimentacaoAtivo> movs = movimentacaoRepository.findAll();
        assertEquals(1, movs.size());
        assertEquals(ConciliacaoInvestimento.CONCILIADA, movs.get(0).getConciliacao());
        assertNotNull(movs.get(0).getOperacao());
        assertEquals(TipoOperacaoFinanceira.INVESTIMENTO, movs.get(0).getOperacao().getTipo());
    }

    @Test
    void snapshotExternoNaoMovimentaCaixaEFicaNaoConciliado() {
        MovimentacaoResponse response = investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                compra(BigDecimal.TEN, new BigDecimal("10.00"), null, true));

        assertEquals(ConciliacaoInvestimento.EXTERNO, response.getConciliacao());
        assertEquals(null, response.getOperacaoId());

        assertEquals(0, new BigDecimal("2000.00").compareTo(
                carteiraRepository.findById(caixa.getId()).orElseThrow().getSaldo()));
        List<MovimentacaoAtivo> movs = movimentacaoRepository.findAll();
        assertEquals(ConciliacaoInvestimento.EXTERNO, movs.get(0).getConciliacao());
        assertEquals(null, movs.get(0).getOperacao());
    }

    @Test
    void bonificacaoNaoExigeCaixaEPermaneceConciliada() {
        MovimentacaoRequest bonificacao = new MovimentacaoRequest();
        bonificacao.setTipo("BONIFICACAO");
        bonificacao.setData(LocalDate.of(2026, 7, 12));
        bonificacao.setQuantidade(new BigDecimal("5"));
        bonificacao.setPrecoUnitario(BigDecimal.ZERO);

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, bonificacao);

        List<MovimentacaoAtivo> movs = movimentacaoRepository.findAll();
        assertEquals(ConciliacaoInvestimento.CONCILIADA, movs.get(0).getConciliacao());
    }
}
