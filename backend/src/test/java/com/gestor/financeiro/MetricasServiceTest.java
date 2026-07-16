package com.gestor.financeiro;

import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.dto.AtivoRequest;
import com.gestor.financeiro.dto.MovimentacaoRequest;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.model.enums.ModalidadeMeta;
import com.gestor.financeiro.model.enums.StatusMeta;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.FaturaLancamentoRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.CartaoService;
import com.gestor.financeiro.service.InvestimentoService;
import com.gestor.financeiro.service.MetaService;
import com.gestor.financeiro.service.MetricasService;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.service.TransferenciaService;
import com.gestor.financeiro.service.TransferirCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-F2-15 — As 9 metricas oficiais (ADR-0013) em cenario completo com caixa,
 * cartao, transferencia, cofre de meta e investimento: fonte unica, mesma
 * historia em todas.
 *
 * Cenario (mes corrente do Clock):
 *  - Corrente: 1000 inicial; entrada salario +2000; saida mercado -300
 *  - Transferencia 200 corrente -> poupanca (100 inicial)
 *  - Compra cartao 400 a vista (competencia do mes)
 *  - Reserva de meta (cofre real): 250 da corrente
 *  - Compra de investimento: 10 x 30 = 300 da corrente, cotacao 35
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MetricasServiceTest {

    @Autowired MetricasService metricasService;
    @Autowired TransacaoService transacaoService;
    @Autowired TransferenciaService transferenciaService;
    @Autowired MetaService metaService;
    @Autowired CartaoService cartaoService;
    @Autowired InvestimentoService investimentoService;
    @Autowired CarteiraRepository carteiraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetaRepository metaRepository;
    @Autowired ParcelaRepository parcelaRepository;
    @Autowired FaturaCartaoRepository faturaCartaoRepository;
    @Autowired FaturaLancamentoRepository faturaLancamentoRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Carteira corrente;
    private Conta cartao;
    private Transacao compraCartao;
    private LocalDate hoje;

    @BeforeEach
    void setUp() {
        hoje = LocalDate.now(clock);
        usuario = new Usuario();
        usuario.setNome("Metricas F2");
        usuario.setEmail("metricas-f2@teste.com");
        usuario.setSenha("x");
        usuario = usuarioRepository.save(usuario);

        corrente = novaConta("Corrente", new BigDecimal("1000.00"));
        Carteira poupanca = novaConta("Poupanca", new BigDecimal("100.00"));

        transacao(TipoTransacao.ENTRADA, new BigDecimal("2000.00"), corrente);
        transacao(TipoTransacao.SAIDA, new BigDecimal("300.00"), corrente);

        transferenciaService.transferir(new TransferirCommand(
                usuario.getId(), corrente.getId(), poupanca.getId(),
                new BigDecimal("200.00"), "Guardar", null, null));

        cartao = new Conta();
        cartao.setNome("Cartao");
        cartao.setDiaFechamento(28);
        cartao.setDiaVencimento(10);
        cartao = cartaoService.criar(cartao, usuario.getId());
        compraCartao = new Transacao();
        compraCartao.setDescricao("Notebook");
        compraCartao.setValorTotal(new BigDecimal("400.00"));
        compraCartao.setTipo(TipoTransacao.SAIDA);
        compraCartao.setData(hoje);
        compraCartao.setConta(cartao);
        compraCartao.setParcelado(false);
        compraCartao = transacaoService.criar(compraCartao, usuario.getId());

        Meta meta = new Meta();
        meta.setNome("Viagem");
        meta.setValorTotal(new BigDecimal("1000.00"));
        meta = metaService.criar(meta, usuario.getId());
        metaService.adicionarValor(meta.getId(), new BigDecimal("250.00"),
                corrente.getId(), usuario.getId());

        AtivoRequest ativo = new AtivoRequest();
        ativo.setTicker("NEXO3");
        ativo.setNome("Nexos");
        ativo.setTipo("ACAO");
        ativo.setValorAtual(new BigDecimal("35.00"));
        Long ativoId = investimentoService.criarAtivo(usuario.getId(), ativo).getId();
        MovimentacaoRequest compra = new MovimentacaoRequest();
        compra.setTipo("COMPRA");
        compra.setData(hoje);
        compra.setQuantidade(BigDecimal.TEN);
        compra.setPrecoUnitario(new BigDecimal("30.00"));
        compra.setCarteiraId(corrente.getId());
        investimentoService.adicionarMovimentacao(usuario.getId(), ativoId, compra);
    }

    private Carteira novaConta(String nome, BigDecimal saldo) {
        Carteira c = new Carteira();
        c.setNome(nome);
        c.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        c.setSaldo(saldo);
        c.setUsuario(usuario);
        return carteiraRepository.save(c);
    }

    private void transacao(TipoTransacao tipo, BigDecimal valor, Carteira carteira) {
        Transacao t = new Transacao();
        t.setDescricao(tipo.name());
        t.setValorTotal(valor);
        t.setTipo(tipo);
        t.setData(hoje);
        t.setCarteira(carteira);
        t.setParcelado(false);
        transacaoService.criar(t, usuario.getId());
    }

    @Test
    void noveMetricasContamAMesmaHistoria() {
        MetricasService.Metricas m = metricasService.calcular(usuario.getId());

        // Caixa: corrente 1000+2000-300-200-250-300=1950; poupanca 100+200=300.
        // Disponivel agora inclui o COFRE (250, liquidez IMEDIATA): 1950+300+250
        assertEquals(0, new BigDecimal("2500.00").compareTo(m.disponivelAgora()));

        // Reservado: cofre da meta
        assertEquals(0, new BigDecimal("250.00").compareTo(m.reservado()));

        // Comprometido: fatura fecha dia 28 e vence dia 10 do mes seguinte —
        // fora do horizonte default (fim do mes). Fatura distante nao entra so
        // por estar aberta (ADR-0013)
        assertEquals(0, BigDecimal.ZERO.compareTo(m.comprometido()));

        // Disponivel para gastar = 2500 - 250 - 0
        assertEquals(0, new BigDecimal("2250.00").compareTo(m.disponivelParaGastar()));

        // Com horizonte estendido ate o vencimento, a fatura entra nos 400
        MetricasService.Metricas estendido = metricasService.calcular(
                usuario.getId(), hoje, hoje.plusMonths(2));
        assertEquals(0, new BigDecimal("400.00").compareTo(estendido.comprometido()));

        // Investido: 10 x 35 (cotacao datada)
        assertEquals(0, new BigDecimal("350.00").compareTo(m.investido()));

        // Dividas: passivo do cartao
        assertEquals(0, new BigDecimal("400.00").compareTo(m.dividas()));

        // Resultado mensal (competencia): 2000 - (300 caixa + 400 cartao) = 1300;
        // transferencia, reserva e compra de investimento fora
        assertEquals(0, new BigDecimal("1300.00").compareTo(m.resultadoMensal()));

        // Patrimonio: ativos (1950+300+250) + investido 350 - passivo 400 = 2450
        assertEquals(0, new BigDecimal("2450.00").compareTo(m.patrimonioLiquido()));

        // Variacao: caixa (+2000-300-300 invest = +1400; transferencias e reserva
        // sao internas a ATIVO e se anulam) - passivo (+400) + aportes (300) = 1300
        assertEquals(0, new BigDecimal("1300.00").compareTo(m.variacaoPatrimonial().total()));
        assertEquals(0, new BigDecimal("300.00").compareTo(m.variacaoPatrimonial().aportesInvestimento()));

        // Coerencia interna: variacao do mes == resultado mensal neste cenario
        assertEquals(0, m.resultadoMensal().compareTo(m.variacaoPatrimonial().total()));
    }

    @Test
    void drillDownExplicaCadaNumero() {
        MetricasService.Metricas metricas = metricasService.calcular(usuario.getId());
        Map<String, Function<MetricasService.Metricas, BigDecimal>> valores = Map.of(
                "DISPONIVEL_AGORA", MetricasService.Metricas::disponivelAgora,
                "RESERVADO", MetricasService.Metricas::reservado,
                "COMPROMETIDO", MetricasService.Metricas::comprometido,
                "DISPONIVEL_PARA_GASTAR", MetricasService.Metricas::disponivelParaGastar,
                "INVESTIDO", MetricasService.Metricas::investido,
                "DIVIDAS", MetricasService.Metricas::dividas,
                "RESULTADO_MENSAL", MetricasService.Metricas::resultadoMensal,
                "PATRIMONIO_LIQUIDO", MetricasService.Metricas::patrimonioLiquido,
                "VARIACAO_PATRIMONIAL", m -> m.variacaoPatrimonial().total());

        valores.forEach((nome, extrair) -> {
            BigDecimal soma = metricasService.origens(usuario.getId(), nome).stream()
                    .map(MetricasService.Origem::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, extrair.apply(metricas).compareTo(soma), nome);
        });

        var dividas = metricasService.origens(usuario.getId(), "DIVIDAS");
        assertEquals(1, dividas.size());
        assertEquals(0, new BigDecimal("400.00").compareTo(dividas.get(0).valor()));

        var reservado = metricasService.origens(usuario.getId(), "RESERVADO");
        assertTrue(reservado.stream().anyMatch(o -> "COFRE".equals(o.tipo())));

        var investido = metricasService.origens(usuario.getId(), "INVESTIDO");
        assertEquals(1, investido.size());
        assertEquals("NEXO3", investido.get(0).descricao());

        var resultado = metricasService.origens(usuario.getId(), "RESULTADO_MENSAL");
        assertTrue(resultado.stream().allMatch(o -> o.tipo().endsWith("COMPETENCIA")));

        var variacao = metricasService.origens(usuario.getId(), "VARIACAO_PATRIMONIAL");
        assertTrue(variacao.stream().anyMatch(o -> "VARIACAO_CAIXA".equals(o.tipo())));
        assertTrue(variacao.stream().anyMatch(o -> "VARIACAO_PASSIVO".equals(o.tipo())));
        assertTrue(variacao.stream().anyMatch(o -> "APORTES_INVESTIMENTO".equals(o.tipo())));
    }

    @Test
    void drillDownReplicaFiltrosDeMetaArquivadaRolloverEParcelaDeCartao() {
        Meta arquivada = new Meta();
        arquivada.setUsuario(usuario);
        arquivada.setNome("Meta antiga");
        arquivada.setValorTotal(new BigDecimal("100.00"));
        arquivada.setValorReservado(new BigDecimal("77.00"));
        arquivada.setModalidade(ModalidadeMeta.RESERVA_VIRTUAL);
        arquivada.setStatus(StatusMeta.ARQUIVADA);
        arquivada.setAtiva(false);
        metaRepository.save(arquivada);

        Parcela parcelaCartao = new Parcela();
        parcelaCartao.setTransacao(compraCartao);
        parcelaCartao.setNumeroParcela(1);
        parcelaCartao.setTotalParcelas(1);
        parcelaCartao.setValor(new BigDecimal("123.00"));
        parcelaCartao.setDataVencimento(hoje);
        parcelaRepository.save(parcelaCartao);

        FaturaCartao origem = faturaCartaoRepository.findByUsuarioId(usuario.getId()).get(0);
        origem.setDataVencimento(hoje);
        origem = faturaCartaoRepository.save(origem);

        LocalDate mesDestino = hoje.plusMonths(12);
        FaturaCartao destino = new FaturaCartao();
        destino.setUsuario(usuario);
        destino.setConta(cartao);
        destino.setMes(mesDestino.getMonthValue());
        destino.setAno(mesDestino.getYear());
        destino.setDataVencimento(mesDestino);
        destino = faturaCartaoRepository.save(destino);

        FaturaLancamento rollover = new FaturaLancamento();
        rollover.setFatura(destino);
        rollover.setFaturaOrigem(origem);
        rollover.setDescricao("Saldo devedor anterior");
        rollover.setValor(new BigDecimal("400.00"));
        rollover.setDataCompra(hoje);
        rollover.setTipo(TipoFaturaLancamento.SALDO_DEVEDOR_ANTERIOR);
        faturaLancamentoRepository.save(rollover);

        MetricasService.Metricas metricas = metricasService.calcular(usuario.getId());
        assertEquals(0, new BigDecimal("250.00").compareTo(metricas.reservado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(metricas.comprometido()));

        BigDecimal reservado = somaOrigens("RESERVADO");
        BigDecimal comprometido = somaOrigens("COMPROMETIDO");
        assertEquals(0, metricas.reservado().compareTo(reservado));
        assertEquals(0, metricas.comprometido().compareTo(comprometido));
        Long origemId = origem.getId();
        Long parcelaCartaoId = parcelaCartao.getId();
        assertTrue(metricasService.origens(usuario.getId(), "RESERVADO").stream()
                .noneMatch(o -> arquivada.getId().equals(o.id())));
        assertTrue(metricasService.origens(usuario.getId(), "COMPROMETIDO").stream()
                .noneMatch(o -> origemId.equals(o.id()) || parcelaCartaoId.equals(o.id())));
    }

    private BigDecimal somaOrigens(String metrica) {
        return metricasService.origens(usuario.getId(), metrica).stream()
                .map(MetricasService.Origem::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
