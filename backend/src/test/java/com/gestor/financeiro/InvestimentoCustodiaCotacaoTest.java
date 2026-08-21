package com.gestor.financeiro;

import com.gestor.financeiro.dto.AtivoRequest;
import com.gestor.financeiro.dto.AtivoResponse;
import com.gestor.financeiro.dto.MovimentacaoRequest;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InvestimentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F2-14 — Custodia, cotacao datada e liquidez (ADR-0011): valor de mercado
 * so com cotacao datada; custodia exige subtipo CUSTODIA; liquidez declarada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvestimentoCustodiaCotacaoTest {

    @Autowired InvestimentoService investimentoService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Custodia F2");
        usuario.setEmail("custodia-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);
    }

    private AtivoRequest ativo(String ticker, BigDecimal valorAtual, String liquidez, Long custodiaId) {
        AtivoRequest r = new AtivoRequest();
        r.setTicker(ticker);
        r.setNome(ticker);
        r.setTipo("ACAO");
        r.setValorAtual(valorAtual);
        r.setLiquidez(liquidez);
        r.setCustodiaId(custodiaId);
        return r;
    }

    @Test
    void cotacaoInformadaGanhaInstanteEValorDeMercado() {
        AtivoResponse criado = investimentoService.criarAtivo(
                usuario.getId(), ativo("NEXO3", new BigDecimal("25.00"), "D2", null));
        assertNotNull(criado.getCotacaoEm());
        assertEquals("D2", criado.getLiquidez());
        // sem posicao ainda: valorMercado nulo
        assertNull(criado.getValorMercado());

        MovimentacaoRequest compra = new MovimentacaoRequest();
        compra.setTipo("COMPRA");
        compra.setData(LocalDate.of(2026, 7, 10));
        compra.setQuantidade(new BigDecimal("4"));
        compra.setPrecoUnitario(new BigDecimal("20.00"));
        compra.setExterna(true);
        investimentoService.adicionarMovimentacao(usuario.getId(), criado.getId(), compra);

        AtivoResponse comPosicao = investimentoService.listarAtivos(usuario.getId(), PageRequest.of(0, 20)).getContent().get(0);
        // 4 x 25.00 pela ultima cotacao valida
        assertEquals(0, new BigDecimal("100.00").compareTo(comPosicao.getValorMercado()));
    }

    @Test
    void ativoSemCotacaoDatadaNaoTemValorDeMercadoOficial() {
        AtivoResponse criado = investimentoService.criarAtivo(
                usuario.getId(), ativo("SEMC3", null, null, null));
        assertNull(criado.getCotacaoEm());
        assertNull(criado.getValorMercado());
        assertEquals("IMEDIATA", criado.getLiquidez());
    }

    @Test
    void custodiaExigeSubtipoCustodia() {
        Carteira corrente = new Carteira();
        corrente.setNome("Corrente");
        corrente.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        corrente.setSaldo(BigDecimal.ZERO);
        corrente.setUsuario(usuario);
        final Long correnteId = carteiraRepository.save(corrente).getId();

        assertThrows(BusinessException.class, () -> investimentoService.criarAtivo(
                usuario.getId(), ativo("ERRO3", null, null, correnteId)));

        Carteira custodia = new Carteira();
        custodia.setNome("Corretora");
        custodia.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        custodia.setSubtipo(SubtipoContaFinanceira.CUSTODIA);
        custodia.setSaldo(BigDecimal.ZERO);
        custodia.setUsuario(usuario);
        Long custodiaId = carteiraRepository.save(custodia).getId();

        AtivoResponse ok = investimentoService.criarAtivo(
                usuario.getId(), ativo("OK3", null, null, custodiaId));
        assertEquals(custodiaId, ok.getCustodiaId());
    }
}
