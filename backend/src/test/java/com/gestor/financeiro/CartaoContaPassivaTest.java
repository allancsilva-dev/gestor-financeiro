package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CarteiraService;
import com.gestor.financeiro.service.CartaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-F2-06 — Cartao como conta financeira PASSIVO (ADR-0008): pareamento 1:1
 * na criacao, exclusao da listagem legada e do saldo de caixa.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartaoContaPassivaTest {

    @Autowired CartaoService cartaoService;
    @Autowired CarteiraService carteiraService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Cartao F2");
        usuario.setEmail("cartao-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);
    }

    @Test
    void cartaoNasceComContaFinanceiraPassivaVinculada() {
        Conta cartao = new Conta();
        cartao.setNome("Nubank");
        cartao.setDiaFechamento(10);
        cartao.setDiaVencimento(20);

        Conta criada = cartaoService.criar(cartao, usuario.getId());

        Carteira passivo = criada.getContaFinanceira();
        assertNotNull(passivo);
        assertEquals(SubtipoContaFinanceira.CARTAO, passivo.getSubtipo());
        assertEquals(NaturezaContaFinanceira.PASSIVO, passivo.getNatureza());
        assertEquals(0, BigDecimal.ZERO.compareTo(passivo.getSaldo()));
    }

    @Test
    void listagemLegadaOcultaCartaoESomaDeCaixaIgnoraPassivo() {
        Carteira corrente = new Carteira();
        corrente.setNome("Corrente");
        corrente.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        corrente.setSaldo(new BigDecimal("800.00"));
        corrente.setUsuario(usuario);
        carteiraRepository.save(corrente);

        Conta cartao = new Conta();
        cartao.setNome("Cartao");
        cartao.setDiaFechamento(10);
        cartao.setDiaVencimento(20);
        Conta criada = cartaoService.criar(cartao, usuario.getId());
        // passivo devedor simulado
        Carteira passivo = criada.getContaFinanceira();
        passivo.setSaldo(new BigDecimal("300.00"));
        carteiraRepository.save(passivo);

        // legado: so a corrente aparece
        Page<Carteira> legado = carteiraService.listarLegadoPorUsuario(usuario.getId(), Pageable.unpaged());
        assertEquals(1, legado.getTotalElements());
        assertEquals("Corrente", legado.getContent().get(0).getNome());

        // rota nova: as duas
        Page<Carteira> todas = carteiraService.listarPorUsuario(usuario.getId(), Pageable.unpaged());
        assertEquals(2, todas.getTotalElements());

        // saldo de caixa ignora o passivo do cartao
        BigDecimal saldo = carteiraRepository.sumSaldoByUsuarioId(usuario.getId());
        assertEquals(0, new BigDecimal("800.00").compareTo(saldo));
        assertTrue(saldo.compareTo(new BigDecimal("1100.00")) < 0);
    }
}
