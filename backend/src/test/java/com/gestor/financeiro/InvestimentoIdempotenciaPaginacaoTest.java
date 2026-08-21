package com.gestor.financeiro;

import com.gestor.financeiro.dto.AtivoRequest;
import com.gestor.financeiro.dto.AtivoResponse;
import com.gestor.financeiro.dto.MovimentacaoRequest;
import com.gestor.financeiro.dto.MovimentacaoResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentacaoAtivoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InvestimentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * BACKLOG-0081 e BACKLOG-0082: duplo clique nao duplica movimentacao (a chave
 * vem do request, nao do id ja gravado) e as listagens sao paginadas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvestimentoIdempotenciaPaginacaoTest {

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
        usuario.setNome("Idempotencia Invest");
        usuario.setEmail("idempotencia-invest@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        caixa = new Carteira();
        caixa.setNome("Corrente");
        caixa.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        caixa.setSaldo(new BigDecimal("2000.00"));
        caixa.setUsuario(usuario);
        caixa = carteiraRepository.save(caixa);

        ativoId = criarAtivo("NEXO11").getId();
    }

    private AtivoResponse criarAtivo(String ticker) {
        AtivoRequest ativo = new AtivoRequest();
        ativo.setTicker(ticker);
        ativo.setNome(ticker);
        ativo.setTipo("FII");
        return investimentoService.criarAtivo(usuario.getId(), ativo);
    }

    private MovimentacaoRequest compra() {
        MovimentacaoRequest r = new MovimentacaoRequest();
        r.setTipo("COMPRA");
        r.setData(LocalDate.of(2026, 7, 10));
        r.setQuantidade(new BigDecimal("2"));
        r.setPrecoUnitario(new BigDecimal("100.00"));
        r.setCarteiraId(caixa.getId());
        return r;
    }

    @Test
    void mesmaChaveNaoDuplicaMovimentacaoPosicaoNemCaixa() {
        MovimentacaoResponse primeira =
            investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "clique-unico");
        MovimentacaoResponse repetida =
            investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "clique-unico");

        assertEquals(primeira.getId(), repetida.getId());
        assertEquals(1, movimentacaoRepository.findByAtivoIdAndUsuarioIdOrderByDataDesc(ativoId, usuario.getId()).size());

        AtivoResponse posicao = investimentoService
            .listarAtivos(usuario.getId(), PageRequest.of(0, 20)).getContent().get(0);
        assertEquals(0, new BigDecimal("2").compareTo(posicao.getQuantidade()));
        assertEquals(0, new BigDecimal("1800.00").compareTo(
            carteiraRepository.findById(caixa.getId()).orElseThrow().getSaldo()));
    }

    @Test
    void chavesDiferentesRegistramMovimentacoesDiferentes() {
        MovimentacaoResponse primeira =
            investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "clique-1");
        MovimentacaoResponse segunda =
            investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "clique-2");

        assertNotEquals(primeira.getId(), segunda.getId());
        assertEquals(2, movimentacaoRepository.findByAtivoIdAndUsuarioIdOrderByDataDesc(ativoId, usuario.getId()).size());
    }

    @Test
    void semChaveOFluxoAntigoSegueRegistrandoCadaChamada() {
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra());
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra());

        assertEquals(2, movimentacaoRepository.findByAtivoIdAndUsuarioIdOrderByDataDesc(ativoId, usuario.getId()).size());
    }

    @Test
    void chaveDeUmAtivoNaoBloqueiaMovimentacaoDeOutro() {
        Long outroAtivoId = criarAtivo("OUTR11").getId();

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "mesma-chave");
        investimentoService.adicionarMovimentacao(usuario.getId(), outroAtivoId, compra(), "mesma-chave");

        assertEquals(1, movimentacaoRepository.findByAtivoIdAndUsuarioIdOrderByDataDesc(ativoId, usuario.getId()).size());
        assertEquals(1, movimentacaoRepository.findByAtivoIdAndUsuarioIdOrderByDataDesc(outroAtivoId, usuario.getId()).size());
    }

    @Test
    void listagensRespeitamAPaginacao() {
        criarAtivo("SEGU11");
        criarAtivo("TERC11");

        Page<AtivoResponse> primeiraPagina = investimentoService.listarAtivos(usuario.getId(), PageRequest.of(0, 2));
        assertEquals(2, primeiraPagina.getContent().size());
        assertEquals(3, primeiraPagina.getTotalElements());

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "mov-1");
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra(), "mov-2");

        Page<MovimentacaoResponse> movimentacoes =
            investimentoService.listarMovimentacoes(usuario.getId(), ativoId, PageRequest.of(0, 1));
        assertEquals(1, movimentacoes.getContent().size());
        assertEquals(2, movimentacoes.getTotalElements());
    }
}
