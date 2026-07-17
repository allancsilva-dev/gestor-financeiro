package com.gestor.financeiro;

import com.gestor.financeiro.dto.SugestaoCategoriaResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.SugestaoCategoriaService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PR-F3-02 — Sugestao deterministica de categoria: descricao normalizada
 * igual (trim, minusculas, espacos condensados, sem acentos) vence; senao a
 * mais usada em 90 dias do mesmo tipo com empate por menor ID; senao NENHUMA.
 * Read-only: nao cria categoria nem altera lancamento.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SugestaoCategoriaServiceTest {

    @Autowired SugestaoCategoriaService sugestaoService;
    @Autowired TransacaoService transacaoService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Carteira corrente;
    private Categoria padaria;
    private Categoria mercado;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Sugestao F3", "sugestao-f3@teste.com", "x"));
        corrente = carteiraRepository.save(TestDataFactory.carteira(
                usuario, "Corrente", new BigDecimal("100000.00")));
        padaria = categoriaRepository.save(TestDataFactory.categoria(usuario, "Padaria"));
        mercado = categoriaRepository.save(TestDataFactory.categoria(usuario, "Mercado"));
    }

    @Test
    void descricaoNormalizadaIgualVenceEUsaAMaisRecente() {
        transacao("Café da  Manhã", padaria, TipoTransacao.SAIDA, hoje.minusDays(10));
        // Mesma descricao normalizada, mais recente, categoria diferente: vence
        transacao("cafe da manha ", mercado, TipoTransacao.SAIDA, hoje.minusDays(2));
        // Volume nao interfere no criterio 1
        transacao("Feira", padaria, TipoTransacao.SAIDA, hoje.minusDays(1));
        transacao("Feira", padaria, TipoTransacao.SAIDA, hoje.minusDays(1));

        SugestaoCategoriaResponse r = sugestaoService.sugerir(
                usuario.getId(), "  CAFE  DA MANHA", TipoTransacao.SAIDA);

        assertEquals(SugestaoCategoriaService.CRITERIO_DESCRICAO_IGUAL, r.criterio());
        assertEquals(mercado.getId(), r.categoria().id());
    }

    @Test
    void semDescricaoIgualCaiNaMaisUsadaEm90DiasDoMesmoTipoComEmpatePorMenorId() {
        // 2x padaria e 2x mercado nos ultimos 90 dias: empate -> menor ID (padaria)
        transacao("Pao", padaria, TipoTransacao.SAIDA, hoje.minusDays(5));
        transacao("Bolo", padaria, TipoTransacao.SAIDA, hoje.minusDays(4));
        transacao("Feira", mercado, TipoTransacao.SAIDA, hoje.minusDays(3));
        transacao("Acougue", mercado, TipoTransacao.SAIDA, hoje.minusDays(2));
        // Fora da janela de 90 dias: nao conta
        transacao("Feira antiga", mercado, TipoTransacao.SAIDA, hoje.minusDays(120));
        // Outro tipo nao conta
        transacao("Salario", mercado, TipoTransacao.ENTRADA, hoje.minusDays(1));

        SugestaoCategoriaResponse r = sugestaoService.sugerir(
                usuario.getId(), "descricao inedita", TipoTransacao.SAIDA);

        assertEquals(SugestaoCategoriaService.CRITERIO_MAIS_USADA_90_DIAS, r.criterio());
        assertEquals(padaria.getId(), r.categoria().id());
    }

    @Test
    void semHistoricoRetornaNenhumaSemCriarCategoria() {
        long categoriasAntes = categoriaRepository.count();

        SugestaoCategoriaResponse r = sugestaoService.sugerir(
                usuario.getId(), "qualquer coisa", TipoTransacao.SAIDA);

        assertEquals(SugestaoCategoriaService.CRITERIO_NENHUMA, r.criterio());
        assertNull(r.categoria());
        assertEquals(categoriasAntes, categoriaRepository.count());
    }

    @Test
    void naoSugereHistoricoDeOutroUsuarioEValidaEntrada() {
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario(
                "Outro", "outro-sugestao@teste.com", "x"));
        Carteira carteiraOutro = carteiraRepository.save(TestDataFactory.carteira(
                outro, "Corrente", new BigDecimal("1000.00")));
        Categoria categoriaOutro = categoriaRepository.save(
                TestDataFactory.categoria(outro, "Lazer"));
        Transacao t = new Transacao();
        t.setDescricao("Cinema");
        t.setValorTotal(new BigDecimal("50.00"));
        t.setTipo(TipoTransacao.SAIDA);
        t.setData(hoje.minusDays(1));
        t.setCarteira(carteiraOutro);
        t.setCategoria(categoriaOutro);
        t.setParcelado(false);
        transacaoService.criar(t, outro.getId());

        SugestaoCategoriaResponse r = sugestaoService.sugerir(
                usuario.getId(), "Cinema", TipoTransacao.SAIDA);
        assertEquals(SugestaoCategoriaService.CRITERIO_NENHUMA, r.criterio());
        assertNull(r.categoria());

        assertThrows(BusinessException.class,
                () -> sugestaoService.sugerir(usuario.getId(), "   ", TipoTransacao.SAIDA));
        assertThrows(BusinessException.class,
                () -> sugestaoService.sugerir(usuario.getId(), "Cinema", null));
    }

    private void transacao(String descricao, Categoria categoria,
                           TipoTransacao tipo, LocalDate data) {
        Transacao t = new Transacao();
        t.setDescricao(descricao);
        t.setValorTotal(new BigDecimal("10.00"));
        t.setTipo(tipo);
        t.setData(data);
        t.setCarteira(corrente);
        t.setCategoria(categoria);
        t.setParcelado(false);
        transacaoService.criar(t, usuario.getId());
    }
}
