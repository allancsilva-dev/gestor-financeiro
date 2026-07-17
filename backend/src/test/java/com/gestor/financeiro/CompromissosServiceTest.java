package com.gestor.financeiro;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.CompromissosService;
import com.gestor.financeiro.service.ContaFixaService;
import com.gestor.financeiro.service.MetricasService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-F3-01 — Compromissos proximos: total identico a metrica oficial
 * Comprometido (calculo compartilhado), itens FATURA/PARCELA no grupo
 * COMPROMETIDO, contas fixas como PREVISTO fora do total, alerta FALHA_SALDO
 * e validacoes de horizonte/ownership.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompromissosServiceTest {

    @Autowired CompromissosService compromissosService;
    @Autowired MetricasService metricasService;
    @Autowired TransacaoService transacaoService;
    @Autowired CartaoService cartaoService;
    @Autowired ContaFixaService contaFixaService;
    @Autowired ContaFixaRepository contaFixaRepository;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Carteira corrente;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(TestDataFactory.usuario(
                "Compromissos F3", "compromissos-f3@teste.com", "x"));
        corrente = carteiraRepository.save(TestDataFactory.carteira(
                usuario, "Corrente", new BigDecimal("1000.00")));
    }

    @Test
    void totalIgualMetricaComFaturaParcelaEContaFixaPrevistaForaDoTotal() {
        // Fatura: compra a vista de 400 no cartao (vence apos o fim do mes)
        Conta cartao = new Conta();
        cartao.setNome("Cartao");
        cartao.setDiaFechamento(28);
        cartao.setDiaVencimento(10);
        cartao = cartaoService.criar(cartao, usuario.getId());
        Transacao compra = new Transacao();
        compra.setDescricao("Notebook");
        compra.setValorTotal(new BigDecimal("400.00"));
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setData(hoje);
        compra.setConta(cartao);
        compra.setParcelado(false);
        transacaoService.criar(compra, usuario.getId());

        // Parcelado fora do cartao: 300 em 3x (vencem +1m, +2m, +3m)
        Transacao parcelada = new Transacao();
        parcelada.setDescricao("Curso");
        parcelada.setValorTotal(new BigDecimal("300.00"));
        parcelada.setTipo(TipoTransacao.SAIDA);
        parcelada.setData(hoje);
        parcelada.setCarteira(corrente);
        parcelada.setParcelado(true);
        parcelada.setTotalParcelas(3);
        transacaoService.criar(parcelada, usuario.getId());

        // Conta fixa pendente dentro do horizonte: PREVISTO, fora do total
        contaFixa("Aluguel", "800.00", TipoTransacao.SAIDA, hoje, StatusPagamento.PENDENTE);
        // Fora do compromisso: entrada recorrente e conta ja paga
        contaFixa("Salario", "2500.00", TipoTransacao.ENTRADA, hoje, StatusPagamento.PENDENTE);
        contaFixa("Internet", "100.00", TipoTransacao.SAIDA, hoje, StatusPagamento.PAGO);

        LocalDate ate = hoje.plusMonths(2);
        CompromissosService.Compromissos resposta =
                compromissosService.listar(usuario.getId(), ate);

        assertEquals(hoje, resposta.referencia());
        assertEquals(ate, resposta.horizonte());

        // Total identico a metrica oficial do mesmo horizonte
        BigDecimal metrica = metricasService.calcular(usuario.getId(), hoje, ate).comprometido();
        assertEquals(0, metrica.compareTo(resposta.totalComprometido()));
        assertEquals(0, new BigDecimal("600.00").compareTo(resposta.totalComprometido()));

        // Itens COMPROMETIDO somam exatamente o total (fatura 400 + 2 parcelas de 100)
        List<CompromissosService.CompromissoItem> comprometidos = resposta.itens().stream()
                .filter(i -> CompromissosService.GRUPO_COMPROMETIDO.equals(i.grupo())).toList();
        BigDecimal somaItens = comprometidos.stream()
                .map(CompromissosService.CompromissoItem::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, resposta.totalComprometido().compareTo(somaItens));
        assertEquals(1, comprometidos.stream().filter(i -> "FATURA".equals(i.tipo())).count());
        assertEquals(2, comprometidos.stream().filter(i -> "PARCELA".equals(i.tipo())).count());

        // Conta fixa: unica prevista, fora do total, sem alerta
        List<CompromissosService.CompromissoItem> previstos = resposta.itens().stream()
                .filter(i -> CompromissosService.GRUPO_PREVISTO.equals(i.grupo())).toList();
        assertEquals(1, previstos.size());
        assertEquals("CONTA_FIXA", previstos.get(0).tipo());
        assertEquals("Aluguel", previstos.get(0).descricao());
        assertEquals(hoje, previstos.get(0).vencimento());
        assertNull(previstos.get(0).alerta());
    }

    @Test
    void horizonteDefaultEhFimDoMesEValidaLimites() {
        // Fatura vence no mes seguinte: fora do horizonte default (ADR-0013)
        Conta cartao = new Conta();
        cartao.setNome("Cartao");
        cartao.setDiaFechamento(28);
        cartao.setDiaVencimento(10);
        cartao = cartaoService.criar(cartao, usuario.getId());
        Transacao compra = new Transacao();
        compra.setDescricao("Mercado");
        compra.setValorTotal(new BigDecimal("150.00"));
        compra.setTipo(TipoTransacao.SAIDA);
        compra.setData(hoje);
        compra.setConta(cartao);
        compra.setParcelado(false);
        transacaoService.criar(compra, usuario.getId());

        CompromissosService.Compromissos resposta =
                compromissosService.listar(usuario.getId(), null);
        assertEquals(hoje.withDayOfMonth(hoje.lengthOfMonth()), resposta.horizonte());
        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.totalComprometido()));
        assertTrue(resposta.itens().stream()
                .noneMatch(i -> CompromissosService.GRUPO_COMPROMETIDO.equals(i.grupo())));

        assertThrows(BusinessException.class,
                () -> compromissosService.listar(usuario.getId(), hoje.minusDays(1)));
        assertThrows(BusinessException.class,
                () -> compromissosService.listar(usuario.getId(), hoje.plusMonths(13)));
    }

    @Test
    void contaFixaComFalhaDeRecorrenciaCarregaAlertaFalhaSaldo() {
        ContaFixa cara = contaFixa("Financiamento", "5000.00",
                TipoTransacao.SAIDA, hoje, StatusPagamento.PENDENTE);
        cara.setExecucaoAutomatica(true);
        cara.setCarteira(corrente);
        contaFixaRepository.save(cara);

        contaFixaService.realizarAutomatica(cara.getId()); // saldo 1000 < 5000

        CompromissosService.Compromissos resposta =
                compromissosService.listar(usuario.getId(), null);
        CompromissosService.CompromissoItem item = resposta.itens().stream()
                .filter(i -> "CONTA_FIXA".equals(i.tipo())).findFirst().orElseThrow();
        assertEquals(CompromissosService.ALERTA_FALHA_SALDO, item.alerta());
        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.totalComprometido()));
    }

    @Test
    void naoVazaCompromissosDeOutroUsuario() {
        Usuario outro = usuarioRepository.save(TestDataFactory.usuario(
                "Outro", "outro-compromissos@teste.com", "x"));
        contaFixaRepository.save(novaContaFixa(outro, "Aluguel alheio", "700.00",
                TipoTransacao.SAIDA, hoje, StatusPagamento.PENDENTE));

        CompromissosService.Compromissos resposta =
                compromissosService.listar(usuario.getId(), null);
        assertTrue(resposta.itens().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(resposta.totalComprometido()));
    }

    private ContaFixa contaFixa(String nome, String valor, TipoTransacao tipo,
                                LocalDate vencimento, StatusPagamento status) {
        return contaFixaRepository.save(
                novaContaFixa(usuario, nome, valor, tipo, vencimento, status));
    }

    private ContaFixa novaContaFixa(Usuario dono, String nome, String valor, TipoTransacao tipo,
                                    LocalDate vencimento, StatusPagamento status) {
        ContaFixa conta = new ContaFixa();
        conta.setUsuario(dono);
        conta.setNome(nome);
        conta.setValorPlanejado(new BigDecimal(valor));
        conta.setDiaVencimento(vencimento.getDayOfMonth());
        conta.setDataProximoVencimento(vencimento);
        conta.setStatus(status);
        conta.setRecorrente(true);
        conta.setAtivo(true);
        conta.setTipo(tipo);
        conta.setExecucaoAutomatica(false);
        return conta;
    }
}
