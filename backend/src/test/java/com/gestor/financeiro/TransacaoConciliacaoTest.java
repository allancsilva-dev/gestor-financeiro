package com.gestor.financeiro;

import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.EstadoConciliacaoTransacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-05 — Conta financeira obrigatoria em operacao manual de caixa (422) e
 * PENDENTE_CONCILIACAO restrito a importacao/legado (ADR-0009/0013).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransacaoConciliacaoTest {

    @Autowired TransacaoService transacaoService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired ContaRepository contaRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private Carteira carteira;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Conciliacao F2");
        usuario.setEmail("conciliacao-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        carteira = new Carteira();
        carteira.setNome("Corrente");
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        carteira.setSaldo(new BigDecimal("1000.00"));
        carteira.setUsuario(usuario);
        carteira = carteiraRepository.save(carteira);
    }

    private Transacao novaTransacao(TipoTransacao tipo) {
        Transacao t = new Transacao();
        t.setDescricao("Teste");
        t.setValorTotal(new BigDecimal("50.00"));
        t.setTipo(tipo);
        t.setData(LocalDate.now());
        t.setParcelado(false);
        return t;
    }

    @Test
    void transacaoManualSemContaFinanceiraERejeitada() {
        Transacao semCaixa = novaTransacao(TipoTransacao.SAIDA);

        assertThrows(BusinessException.class,
                () -> transacaoService.criar(semCaixa, usuario.getId()));
    }

    @Test
    void transacaoManualComCarteiraEConciliada() {
        Transacao t = novaTransacao(TipoTransacao.SAIDA);
        t.setCarteira(carteira);

        Transacao salva = transacaoService.criar(t, usuario.getId());
        assertEquals(EstadoConciliacaoTransacao.CONCILIADA, salva.getEstadoConciliacao());
    }

    @Test
    void compraDeCartaoSemCarteiraEConciliadaViaFatura() {
        Carteira passivoCartao = carteiraRepository.save(TestDataFactory.contaPassivaCartao(usuario, "Cartao"));
        Conta cartao = TestDataFactory.cartao(usuario, "Cartao", passivoCartao);
        cartao.setDiaFechamento(10);
        cartao.setDiaVencimento(20);
        cartao = contaRepository.save(cartao);

        Transacao compra = novaTransacao(TipoTransacao.SAIDA);
        compra.setConta(cartao);

        Transacao salva = transacaoService.criar(compra, usuario.getId());
        assertEquals(EstadoConciliacaoTransacao.CONCILIADA, salva.getEstadoConciliacao());
    }

    @Test
    void importacaoSemContaFinanceiraEntraPendenteDeConciliacao() {
        Transacao importada = novaTransacao(TipoTransacao.SAIDA);

        Transacao salva = transacaoService.criarImportada(importada, usuario.getId());
        assertEquals(EstadoConciliacaoTransacao.PENDENTE_CONCILIACAO, salva.getEstadoConciliacao());
    }
}
