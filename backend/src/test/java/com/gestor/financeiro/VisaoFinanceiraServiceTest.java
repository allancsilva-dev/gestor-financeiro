package com.gestor.financeiro;

import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.FaturaService;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.service.TransferenciaService;
import com.gestor.financeiro.service.TransferirCommand;
import com.gestor.financeiro.service.VisaoFinanceiraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PR-F2-10 — Reconciliacao cruzada das visoes (ADR-0010): mesma historia em
 * COMPRA, COMPETENCIA e CAIXA; transferencia interna fora de todas.
 *
 * Cenario (julho/2026): entrada caixa 200; saida caixa 50; compra cartao 90 em
 * 3x; transferencia 100 entre contas; pagamento de fatura 30.
 *  COMPRA:       entradas 200, saidas 140 (50 + 90 na data da compra)
 *  COMPETENCIA:  entradas 200, saidas 140 (cartao pela data da compra)
 *  CAIXA:        entradas 200, saidas  80 (50 + pagamento 30); compra fora
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VisaoFinanceiraServiceTest {

    /**
     * O cenario e de julho/2026, mas `pagarFatura` carimba `dataPagamento` com
     * `LocalDate.now(clock)`. Com o relogio de sistema o pagamento cai fora da janela
     * INICIO..FIM e a visao CAIXA perde os 30,00. Relogio parado em 20/07/2026.
     */
    static final Instant AGORA = Instant.parse("2026-07-20T15:00:00Z");
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @TestConfiguration
    static class ClockFixoEmJulho2026 {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(AGORA, SAO_PAULO);
        }
    }

    @Autowired VisaoFinanceiraService visaoService;
    @Autowired TransacaoService transacaoService;
    @Autowired TransferenciaService transferenciaService;
    @Autowired CartaoService cartaoService;
    @Autowired FaturaService faturaService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired FaturaCartaoRepository faturaCartaoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private static final LocalDate INICIO = LocalDate.of(2026, 7, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 7, 31);

    private Usuario usuario;
    private Carteira corrente;
    private Carteira poupanca;
    private Conta cartao;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNome("Visao F2");
        usuario.setEmail("visao-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        corrente = novaConta("Corrente", new BigDecimal("1000.00"));
        poupanca = novaConta("Poupanca", BigDecimal.ZERO);

        Conta novo = new Conta();
        novo.setNome("Cartao");
        novo.setDiaFechamento(25);
        novo.setDiaVencimento(5);
        cartao = cartaoService.criar(novo, usuario.getId());

        // entrada 200 e saida 50 em caixa
        criarTransacao(TipoTransacao.ENTRADA, new BigDecimal("200.00"), corrente, null);
        criarTransacao(TipoTransacao.SAIDA, new BigDecimal("50.00"), corrente, null);

        // compra de cartao 90 em 3x (data 05/07; todas as parcelas competem a julho)
        Transacao compra = new Transacao();
        compra.setDescricao("Compra cartao");
        compra.setValorTotal(new BigDecimal("90.00"));
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setData(LocalDate.of(2026, 7, 5));
        compra.setConta(cartao);
        compra.setParcelado(true);
        compra.setTotalParcelas(3);
        transacaoService.criar(compra, usuario.getId());

        // transferencia interna 100 (fora de todas as visoes de consumo)
        transferenciaService.transferir(new TransferirCommand(
                usuario.getId(), corrente.getId(), poupanca.getId(),
                new BigDecimal("100.00"), "Guardar", null,
                LocalDate.of(2026, 7, 10).atStartOfDay()));

        // pagamento parcial de fatura 30 (so visao CAIXA)
        Long faturaId = faturaCartaoRepository.findAll().stream()
                .filter(f -> f.getConta().getId().equals(cartao.getId()))
                .findFirst().orElseThrow().getId();
        faturaService.pagarFatura(usuario.getId(), faturaId, new BigDecimal("30.00"),
                corrente.getId(), "pag-visao");
    }

    private Carteira novaConta(String nome, BigDecimal saldo) {
        Carteira c = new Carteira();
        c.setNome(nome);
        c.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        c.setSaldo(saldo);
        c.setUsuario(usuario);
        return carteiraRepository.save(c);
    }

    private void criarTransacao(TipoTransacao tipo, BigDecimal valor, Carteira carteira, Conta conta) {
        Transacao t = new Transacao();
        t.setDescricao(tipo.name());
        t.setValorTotal(valor);
        t.setTipo(tipo);
        t.setData(LocalDate.of(2026, 7, 3));
        t.setCarteira(carteira);
        t.setConta(conta);
        t.setParcelado(false);
        transacaoService.criar(t, usuario.getId());
    }

    @Test
    void visoesContamAMesmaHistoriaETransferenciaFicaFora() {
        VisaoFinanceiraService.Visoes visoes = visaoService.resumo(usuario.getId(), INICIO, FIM);

        // COMPRA
        assertEquals(0, new BigDecimal("200.00").compareTo(visoes.compra().entradas()));
        assertEquals(0, new BigDecimal("140.00").compareTo(visoes.compra().saidas()));

        // COMPETENCIA reconcilia com COMPRA (cartao pela data da compra)
        assertEquals(0, new BigDecimal("200.00").compareTo(visoes.competencia().entradas()));
        assertEquals(0, new BigDecimal("140.00").compareTo(visoes.competencia().saidas()));

        // CAIXA: so o que moveu dinheiro (50 + pagamento 30); compra de cartao fora
        assertEquals(0, new BigDecimal("200.00").compareTo(visoes.caixa().entradas()));
        assertEquals(0, new BigDecimal("80.00").compareTo(visoes.caixa().saidas()));
    }
}
