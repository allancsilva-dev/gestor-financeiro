package com.gestor.financeiro;

import com.gestor.financeiro.dto.AtivoRequest;
import com.gestor.financeiro.dto.AtivoResponse;
import com.gestor.financeiro.dto.MovimentacaoRequest;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Ativo;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.repository.AtivoRepository;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.InvestimentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvestimentoServiceTest {

    @Autowired
    private InvestimentoService investimentoService;

    @Autowired
    private AtivoRepository ativoRepository;

    @Autowired
    private CarteiraRepository carteiraRepository;

    @Autowired
    private MovimentoCarteiraRepository movimentoCarteiraRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario usuario;
    private Long ativoId;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Investidor", "investimento-service@teste.com", passwordEncoder.encode("123456")));

        AtivoRequest req = new AtivoRequest();
        req.setTicker("itsa4");
        req.setNome("Itausa");
        req.setTipo("ACAO");
        req.setValorAtual(new BigDecimal("10.00"));
        AtivoResponse ativo = investimentoService.criarAtivo(usuario.getId(), req);
        ativoId = ativo.getId();
    }

    // ---- validacao / integridade de posicao (bug central PROB-0054) ----

    @Test
    void vendaAcimaDaPosicaoLancaBusinessException() {
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "10", "10.00", null));

        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("VENDA", "15", "12.00", null)));

        Ativo ativo = ativoRepository.findById(ativoId).orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(ativo.getQuantidade()),
                "posicao nao pode ficar negativa apos venda rejeitada");
    }

    @Test
    void vendaSemPosicaoNaoDividePorZero() {
        // Ativo recem criado tem quantidade 0; venda deve dar erro de negocio, nao ArithmeticException.
        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("VENDA", "1", "12.00", null)));
    }

    @Test
    void quantidadeNaoPositivaRejeitada() {
        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("COMPRA", "0", "10.00", null)));
        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("COMPRA", "-5", "10.00", null)));
    }

    @Test
    void precoNaoPositivoRejeitadoExcetoBonificacao() {
        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("COMPRA", "10", "0", null)));
    }

    @Test
    void tipoInvalidoLancaBusinessException() {
        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("FOO", "10", "10.00", null)));
    }

    // ---- dividendo / bonificacao (padrao de mercado) ----

    @Test
    void bonificacaoAumentaQuantidadeSemCusto() {
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "100", "10.00", null)); // custo 1000, qtd 100
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("BONIFICACAO", "10", "0", null)); // +10 gratis

        Ativo ativo = ativoRepository.findById(ativoId).orElseThrow();
        assertEquals(0, new BigDecimal("110").compareTo(ativo.getQuantidade()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(ativo.getCustoTotal()),
                "bonificacao nao acrescenta custo -> preco medio cai");
    }

    @Test
    void dividendoNaoAlteraPosicao() {
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "100", "10.00", null));
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("DIVIDENDO", "100", "0.50", null)); // provento 50, sem carteira

        Ativo ativo = ativoRepository.findById(ativoId).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(ativo.getQuantidade()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(ativo.getCustoTotal()));
    }

    // ---- integracao de caixa (carteira opcional) ----

    @Test
    void semCarteiraNaoGeraMovimentoDeCaixa() {
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "10", "10.00", null));
        assertEquals(0, movimentoCarteiraRepository.count());
    }

    @Test
    void compraComCarteiraDebitaCaixa() {
        Carteira carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corretora", new BigDecimal("1000.00")));

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "10", "10.00", carteira.getId())); // -100

        Carteira atual = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("900.00").compareTo(atual.getSaldo()));
        assertEquals(1, movimentoCarteiraRepository.count());
    }

    @Test
    void vendaComCarteiraCreditaCaixa() {
        Carteira carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corretora", new BigDecimal("1000.00")));

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "10", "10.00", carteira.getId())); // -100 -> 900
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("VENDA", "4", "15.00", carteira.getId())); // +60 -> 960

        Carteira atual = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("960.00").compareTo(atual.getSaldo()));
    }

    @Test
    void dividendoComCarteiraCreditaCaixaSemAlterarPosicao() {
        Carteira carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corretora", new BigDecimal("1000.00")));

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "100", "10.00", carteira.getId())); // -1000 -> 0
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("DIVIDENDO", "100", "0.50", carteira.getId())); // +50 -> 50

        Carteira atual = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("50.00").compareTo(atual.getSaldo()));

        Ativo ativo = ativoRepository.findById(ativoId).orElseThrow();
        assertEquals(0, new BigDecimal("100").compareTo(ativo.getQuantidade()));
    }

    @Test
    void compraComCarteiraSemSaldoLancaBusinessException() {
        Carteira carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corretora", new BigDecimal("50.00")));

        assertThrows(BusinessException.class, () ->
                investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                        mov("COMPRA", "10", "10.00", carteira.getId()))); // precisa 100, tem 50
    }

    @Test
    void bonificacaoComCarteiraNaoMovimentaCaixa() {
        Carteira carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corretora", new BigDecimal("1000.00")));

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("BONIFICACAO", "10", "0", carteira.getId()));

        Carteira atual = carteiraRepository.findById(carteira.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(atual.getSaldo()));
        assertEquals(0, movimentoCarteiraRepository.count());
    }

    @Test
    void movimentoDeInvestimentoUsaOrigemInvestimento() {
        Carteira carteira = carteiraRepository.save(
                TestDataFactory.carteira(usuario, "Corretora", new BigDecimal("1000.00")));

        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId,
                mov("COMPRA", "10", "10.00", carteira.getId()));

        assertEquals(OrigemMovimentoCarteira.INVESTIMENTO,
                movimentoCarteiraRepository.findAll().get(0).getOrigem());
    }

    private MovimentacaoRequest mov(String tipo, String qtd, String preco, Long carteiraId) {
        MovimentacaoRequest r = new MovimentacaoRequest();
        r.setTipo(tipo);
        r.setData(LocalDate.now());
        r.setQuantidade(new BigDecimal(qtd));
        r.setPrecoUnitario(new BigDecimal(preco));
        r.setCarteiraId(carteiraId);
        return r;
    }
}
