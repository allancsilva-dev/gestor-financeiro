package com.gestor.financeiro;

import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-F3-04 — Filtros opcionais categoriaId/carteiraId/cartaoId em
 * /v1/transacoes/periodo, combinaveis com periodo, tipo e busca; recurso
 * alheio segue o contrato seguro existente (404).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransacaoPeriodoFiltrosTest {

    @Autowired TransacaoService transacaoService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CartaoService cartaoService;
    @Autowired Clock clock;

    private Usuario usuario;
    private Carteira corrente;
    private Carteira poupanca;
    private Categoria mercado;
    private Categoria lazer;
    private Conta cartao;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Filtros F3", "filtros-f3@teste.com", "x"));
        corrente = carteiraRepository.save(TestDataFactory.carteira(
                usuario, "Corrente", new BigDecimal("10000.00")));
        poupanca = carteiraRepository.save(TestDataFactory.carteira(
                usuario, "Poupanca", new BigDecimal("5000.00")));
        mercado = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
        lazer = categoriaRepository.save(TestDataFactory.categoria(usuario, "Lazer"));
        Conta novoCartao = new Conta();
        novoCartao.setNome("Cartao");
        novoCartao.setDiaFechamento(28);
        novoCartao.setDiaVencimento(10);
        cartao = cartaoService.criar(novoCartao, usuario.getId());

        criar("Feira", mercado, corrente, null, TipoTransacao.SAIDA);
        criar("Cinema", lazer, corrente, null, TipoTransacao.SAIDA);
        criar("Poupanca lazer", lazer, poupanca, null, TipoTransacao.SAIDA);
        criar("Notebook no cartao", lazer, null, cartao, TipoTransacao.SAIDA);
        criar("Salario", null, corrente, null, TipoTransacao.ENTRADA);
    }

    @Test
    void filtraPorCategoriaCarteiraECartaoCombinadosComTipoEBusca() {
        assertEquals(3, listar(null, null, lazer.getId(), null, null).getTotalElements());
        assertEquals(1, listar(null, null, null, poupanca.getId(), null).getTotalElements());
        assertEquals(1, listar(null, null, null, null, cartao.getId()).getTotalElements());
        assertEquals("Notebook no cartao",
                listar(null, null, null, null, cartao.getId()).getContent().get(0).getDescricao());

        // Combinados: categoria + carteira; categoria + tipo + busca
        assertEquals(1, listar(null, null, lazer.getId(), poupanca.getId(), null).getTotalElements());
        assertEquals(1, listar(TipoTransacao.SAIDA, "cinema", lazer.getId(), null, null).getTotalElements());
        assertEquals(0, listar(TipoTransacao.ENTRADA, null, lazer.getId(), null, null).getTotalElements());

        // Sem filtros novos: caminho legado intacto
        assertEquals(5, listar(null, null, null, null, null).getTotalElements());
        assertEquals(4, listar(TipoTransacao.SAIDA, null, null, null, null).getTotalElements());
    }

    @Test
    void recursoAlheioRetornaContratoSeguroExistente() {
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario(
                "Outro", "outro-filtros@teste.com", "x"));
        Carteira carteiraOutro = carteiraRepository.save(TestDataFactory.carteira(
                outro, "Corrente", new BigDecimal("100.00")));
        Categoria categoriaOutro = categoriaRepository.save(
                TestDataFactory.categoria(outro, "Alheia"));

        assertThrows(ResourceNotFoundException.class,
                () -> listar(null, null, categoriaOutro.getId(), null, null));
        assertThrows(ResourceNotFoundException.class,
                () -> listar(null, null, null, carteiraOutro.getId(), null));
        assertThrows(ResourceNotFoundException.class,
                () -> listar(null, null, null, null, 999999L));

        // Transacoes de outro usuario nunca aparecem no proprio filtro valido
        assertTrue(listar(null, null, lazer.getId(), null, null).getContent().stream()
                .allMatch(t -> t.getUsuario().getId().equals(usuario.getId())));
    }

    private Page<Transacao> listar(TipoTransacao tipo, String q,
                                   Long categoriaId, Long carteiraId, Long cartaoId) {
        return transacaoService.listarPorPeriodo(usuario.getId(),
                hoje.minusDays(30), hoje.plusDays(1), tipo, q,
                categoriaId, carteiraId, cartaoId, Pageable.unpaged());
    }

    private void criar(String descricao, Categoria categoria, Carteira carteira,
                       Conta conta, TipoTransacao tipo) {
        Transacao t = new Transacao();
        t.setDescricao(descricao);
        t.setValorTotal(new BigDecimal("100.00"));
        t.setTipo(tipo);
        t.setData(hoje);
        t.setCategoria(categoria);
        t.setCarteira(carteira);
        t.setConta(conta);
        t.setParcelado(false);
        transacaoService.criar(t, usuario.getId());
    }
}
