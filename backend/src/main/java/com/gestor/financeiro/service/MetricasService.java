package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.CartaoResumoDto;
import com.gestor.financeiro.dto.CategoriaResumoDto;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.ModalidadeMeta;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.StatusMeta;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.TipoMovimentacao;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.AtivoRepository;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.FaturaCartaoRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.MovimentacaoAtivoRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import com.gestor.financeiro.repository.ParcelaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * As 9 metricas oficiais do produto (ADR-0013, PR-F2-15), calculadas de fonte
 * unica (ledger + faturas abertas + alocacoes + posicoes), com data de
 * referencia, horizonte e politica explicitos. A mesma metrica produz o mesmo
 * valor em toda superficie que a exibe.
 */
@Service
public class MetricasService {

    /** Vencidas nao pagas entram no Comprometido independentemente da idade. */
    private static final LocalDate INICIO_OBRIGACOES = LocalDate.of(2000, 1, 1);

    private final CarteiraRepository carteiraRepository;
    private final MetaRepository metaRepository;
    private final FaturaCartaoRepository faturaCartaoRepository;
    private final ParcelaRepository parcelaRepository;
    private final AtivoRepository ativoRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;
    private final MovimentacaoAtivoRepository movimentacaoAtivoRepository;
    private final VisaoFinanceiraService visaoFinanceiraService;
    private final Clock clock;

    public MetricasService(CarteiraRepository carteiraRepository,
                           MetaRepository metaRepository,
                           FaturaCartaoRepository faturaCartaoRepository,
                           ParcelaRepository parcelaRepository,
                           AtivoRepository ativoRepository,
                           MovimentoCarteiraRepository movimentoCarteiraRepository,
                           MovimentacaoAtivoRepository movimentacaoAtivoRepository,
                           VisaoFinanceiraService visaoFinanceiraService,
                           Clock clock) {
        this.carteiraRepository = carteiraRepository;
        this.metaRepository = metaRepository;
        this.faturaCartaoRepository = faturaCartaoRepository;
        this.parcelaRepository = parcelaRepository;
        this.ativoRepository = ativoRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
        this.movimentacaoAtivoRepository = movimentacaoAtivoRepository;
        this.visaoFinanceiraService = visaoFinanceiraService;
        this.clock = clock;
    }

    public record VariacaoPatrimonial(
            BigDecimal total,
            BigDecimal caixa,
            BigDecimal passivo,
            BigDecimal aportesInvestimento,
            BigDecimal rendimentosInvestimento,
            BigDecimal precoMercado // null: sem historico de cotacao nesta fase
    ) {
    }

    public record Metricas(
            LocalDate dataReferencia,
            LocalDate horizonteComprometido,
            BigDecimal disponivelAgora,
            BigDecimal reservado,
            BigDecimal comprometido,
            BigDecimal disponivelParaGastar,
            BigDecimal investido,
            BigDecimal dividas,
            BigDecimal resultadoMensal,
            BigDecimal patrimonioLiquido,
            VariacaoPatrimonial variacaoPatrimonial
    ) {
    }

    public Metricas calcular(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        return calcular(usuarioId, hoje, hoje.withDayOfMonth(hoje.lengthOfMonth()));
    }

    public Metricas calcular(Long usuarioId, LocalDate dataReferencia, LocalDate horizonte) {
        if (horizonte.isBefore(dataReferencia)) {
            throw new BusinessException("Horizonte não pode ser anterior à data de referência");
        }

        BigDecimal disponivelAgora = carteiraRepository.sumDisponivelAgora(usuarioId);

        BigDecimal reservado = carteiraRepository
                .sumSaldoPorSubtipo(usuarioId, SubtipoContaFinanceira.COFRE)
                .add(metaRepository.sumReservaVirtual(usuarioId));

        BigDecimal comprometido = comprometido(usuarioId, horizonte);

        // Sem truncar negativos (ADR-0013)
        BigDecimal disponivelParaGastar = disponivelAgora.subtract(reservado).subtract(comprometido);

        BigDecimal investido = ativoRepository.sumValorMercadoCotado(usuarioId);
        BigDecimal dividas = carteiraRepository.sumDividas(usuarioId);

        // Resultado mensal: competencia (ADR-0010/0013); transferencias, reservas,
        // investimento e pagamento de cartao ja ficam fora por construcao
        LocalDate inicioMes = dataReferencia.withDayOfMonth(1);
        LocalDate fimMes = dataReferencia.withDayOfMonth(dataReferencia.lengthOfMonth());
        VisaoFinanceiraService.ResumoVisao competencia =
                visaoFinanceiraService.resumo(usuarioId, inicioMes, fimMes).competencia();
        BigDecimal resultadoMensal = competencia.entradas().subtract(competencia.saidas());

        BigDecimal patrimonioLiquido = carteiraRepository.sumAtivoTotal(usuarioId)
                .add(investido)
                .subtract(carteiraRepository.sumPassivoAssinado(usuarioId));

        VariacaoPatrimonial variacao = variacaoPatrimonial(usuarioId, inicioMes, dataReferencia);

        return new Metricas(dataReferencia, horizonte, disponivelAgora, reservado, comprometido,
                disponivelParaGastar, investido, dividas, resultadoMensal, patrimonioLiquido,
                variacao);
    }

    /**
     * Variacao patrimonial do periodo com decomposicao (ADR-0013): delta de
     * caixa e passivo pelo ledger (BACKFILL fora), aportes liquidos e
     * rendimentos de investimento pelo custo. Componente de preco de mercado e
     * null nesta fase (sem historico de cotacao).
     */
    private VariacaoPatrimonial variacaoPatrimonial(Long usuarioId, LocalDate inicio, LocalDate fim) {
        var inicioTs = inicio.atStartOfDay();
        var fimTs = fim.plusDays(1).atStartOfDay().minusNanos(1);

        BigDecimal caixa = movimentoCarteiraRepository.sumVariacaoPorNatureza(
                usuarioId, NaturezaContaFinanceira.ATIVO, inicioTs, fimTs);
        BigDecimal passivo = movimentoCarteiraRepository.sumVariacaoPorNatureza(
                usuarioId, NaturezaContaFinanceira.PASSIVO, inicioTs, fimTs);

        BigDecimal compras = movimentacaoAtivoRepository.sumValorPorTipoNoPeriodo(
                usuarioId, TipoMovimentacao.COMPRA, inicio, fim);
        BigDecimal vendas = movimentacaoAtivoRepository.sumValorPorTipoNoPeriodo(
                usuarioId, TipoMovimentacao.VENDA, inicio, fim);
        BigDecimal dividendos = movimentacaoAtivoRepository.sumValorPorTipoNoPeriodo(
                usuarioId, TipoMovimentacao.DIVIDENDO, inicio, fim);
        BigDecimal aportes = compras.subtract(vendas);

        BigDecimal total = caixa.subtract(passivo).add(aportes);
        return new VariacaoPatrimonial(total, caixa, passivo, aportes, dividendos, null);
    }

    // --- Comprometido compartilhado (PR-F2-15 / PR-F3-01) ---

    /**
     * Valor oficial da metrica Comprometido: vencidas nao pagas + vencimento
     * ate o horizonte; fatura ou parcela distante nao entra so por estar
     * aberta (ADR-0013). Unica fonte do numero — compromissos (PR-F3-01)
     * consomem este mesmo calculo.
     */
    public BigDecimal comprometido(Long usuarioId, LocalDate horizonte) {
        BigDecimal faturas = faturaCartaoRepository.somarSaldoRestanteNoPeriodo(
                usuarioId, FaturaStatus.PAGA, INICIO_OBRIGACOES, horizonte);
        BigDecimal parcelas = parcelaRepository.somarValorNoPeriodo(
                usuarioId, INICIO_OBRIGACOES, horizonte,
                StatusPagamento.PAGO, TipoTransacao.SAIDA);
        return faturas.add(parcelas);
    }

    /**
     * `descricao` segue com o texto pronto ("Compra 6/10") usado no drill-down.
     * Os campos estruturados abaixo existem para a UI montar o carrossel de
     * parcelas sem parsear string: numero/total da parcela, cartao e categoria.
     */
    public record ObrigacaoComprometida(
            String tipo, Long id, String descricao, BigDecimal valor, LocalDate vencimento,
            Long transacaoId, Integer numeroParcela, Integer totalParcelas,
            CartaoResumoDto cartao, CategoriaResumoDto categoria) {
    }

    /**
     * Itens (FATURA/PARCELA) que compoem exatamente o Comprometido do mesmo
     * horizonte: mesmos filtros das somas de {@link #comprometido}.
     */
    public List<ObrigacaoComprometida> obrigacoesComprometidas(Long usuarioId, LocalDate horizonte) {
        List<ObrigacaoComprometida> itens = new ArrayList<>();
        faturaCartaoRepository.findComprometidasNoPeriodo(
                usuarioId, FaturaStatus.PAGA, INICIO_OBRIGACOES, horizonte)
                .forEach(f -> {
                    BigDecimal restante = nvl(f.getValorTotal()).subtract(nvl(f.getValorPago()));
                    if (restante.signum() > 0) {
                        itens.add(new ObrigacaoComprometida("FATURA", f.getId(),
                                "Fatura " + f.getMes() + "/" + f.getAno(), restante,
                                f.getDataVencimento(), null, null, null,
                                CartaoResumoDto.fromEntity(f.getConta()), null));
                    }
                });
        parcelaRepository.findComprometidasNoPeriodo(
                usuarioId, INICIO_OBRIGACOES, horizonte, StatusPagamento.PAGO,
                TipoTransacao.SAIDA)
                .forEach(p -> itens.add(new ObrigacaoComprometida("PARCELA", p.getId(),
                        p.getTransacao().getDescricao() + " " + p.getNumeroParcela() + "/"
                                + p.getTotalParcelas(), p.getValor(), p.getDataVencimento(),
                        p.getTransacao().getId(), p.getNumeroParcela(), p.getTotalParcelas(),
                        CartaoResumoDto.fromEntity(p.getTransacao().getConta()),
                        CategoriaResumoDto.fromEntity(p.getTransacao().getCategoria()))));
        return itens;
    }

    // --- Drill-down (PR-F2-16): origem de cada numero ---

    /**
     * Navegacao fornecida pelo backend (PR-F3-04): destino, ID e filtros
     * necessarios. Origem sem destino exato fica sem navegacao (informativa);
     * o cliente nunca inventa link aproximado.
     */
    public record Navegacao(String destino, Long id, Map<String, String> filtros) {
        public static final String EXTRATO_CONTA = "EXTRATO_CONTA";
        public static final String TRANSACAO = "TRANSACAO";
        public static final String FATURA = "FATURA";
        public static final String META = "META";
        public static final String INVESTIMENTO = "INVESTIMENTO";
        public static final String TRANSACOES = "TRANSACOES";

        static Navegacao para(String destino, Long id) {
            return new Navegacao(destino, id, null);
        }
    }

    public record Origem(String tipo, Long id, String descricao, BigDecimal valor,
                         Navegacao navegacao) {
        public Origem(String tipo, Long id, String descricao, BigDecimal valor) {
            this(tipo, id, descricao, valor, null);
        }
    }

    public List<Origem> origens(Long usuarioId, String metrica) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate horizonte = hoje.withDayOfMonth(hoje.lengthOfMonth());
        return switch (metrica.toUpperCase()) {
            case "DISPONIVEL_AGORA" -> contasOrigem(usuarioId, NaturezaContaFinanceira.ATIVO, true);
            case "RESERVADO" -> reservadoOrigem(usuarioId);
            case "COMPROMETIDO" -> comprometidoOrigem(usuarioId, horizonte);
            case "DISPONIVEL_PARA_GASTAR" -> disponivelParaGastarOrigem(usuarioId, horizonte);
            case "INVESTIDO" -> investidoOrigem(usuarioId);
            case "DIVIDAS" -> dividasOrigem(usuarioId);
            case "RESULTADO_MENSAL" -> resultadoMensalOrigem(usuarioId, hoje);
            case "PATRIMONIO_LIQUIDO" -> patrimonioOrigem(usuarioId);
            case "VARIACAO_PATRIMONIAL" -> variacaoPatrimonialOrigem(usuarioId, hoje);
            default -> throw new BusinessException(
                    "Métrica sem drill-down disponível: " + metrica);
        };
    }

    private List<Origem> contasOrigem(Long usuarioId, NaturezaContaFinanceira natureza, boolean soImediata) {
        List<Origem> origens = new ArrayList<>();
        for (Carteira c : carteiraRepository.findByUsuarioIdAndNatureza(usuarioId, natureza)) {
            if (soImediata && c.getLiquidez() != com.gestor.financeiro.model.enums.LiquidezContaFinanceira.IMEDIATA) {
                continue;
            }
            origens.add(new Origem("CONTA_FINANCEIRA", c.getId(), c.getNome(), c.getSaldo(),
                    Navegacao.para(Navegacao.EXTRATO_CONTA, c.getId())));
        }
        return origens;
    }

    private List<Origem> reservadoOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>();
        for (Carteira cofre : carteiraRepository.findByUsuarioIdAndSubtipo(
                usuarioId, SubtipoContaFinanceira.COFRE)) {
            origens.add(new Origem("COFRE", cofre.getId(), cofre.getNome(), cofre.getSaldo(),
                    Navegacao.para(Navegacao.EXTRATO_CONTA, cofre.getId())));
        }
        for (Meta meta : metaRepository.findByUsuarioIdAndModalidadeAndStatusNot(
                usuarioId, ModalidadeMeta.RESERVA_VIRTUAL, StatusMeta.ARQUIVADA)) {
            if (meta.getValorReservado() != null && meta.getValorReservado().signum() > 0) {
                origens.add(new Origem("ALOCACAO_VIRTUAL", meta.getId(),
                        "Meta: " + meta.getNome(), meta.getValorReservado(),
                        Navegacao.para(Navegacao.META, meta.getId())));
            }
        }
        return origens;
    }

    private List<Origem> comprometidoOrigem(Long usuarioId, LocalDate horizonte) {
        return obrigacoesComprometidas(usuarioId, horizonte).stream()
                .map(o -> new Origem(o.tipo(), o.id(), o.descricao(), o.valor(),
                        navegacaoObrigacao(o)))
                .toList();
    }

    private Navegacao navegacaoObrigacao(ObrigacaoComprometida o) {
        if ("FATURA".equals(o.tipo())) {
            return Navegacao.para(Navegacao.FATURA, o.id());
        }
        if ("PARCELA".equals(o.tipo()) && o.transacaoId() != null) {
            return Navegacao.para(Navegacao.TRANSACAO, o.transacaoId());
        }
        return null;
    }

    private List<Origem> disponivelParaGastarOrigem(Long usuarioId, LocalDate horizonte) {
        List<Origem> origens = new ArrayList<>(
                contasOrigem(usuarioId, NaturezaContaFinanceira.ATIVO, true));
        reservadoOrigem(usuarioId).forEach(o -> origens.add(
                new Origem(o.tipo(), o.id(), o.descricao(), o.valor().negate(), o.navegacao())));
        comprometidoOrigem(usuarioId, horizonte).forEach(o -> origens.add(
                new Origem(o.tipo(), o.id(), o.descricao(), o.valor().negate(), o.navegacao())));
        return origens;
    }

    private List<Origem> resultadoMensalOrigem(Long usuarioId, LocalDate referencia) {
        LocalDate inicio = referencia.withDayOfMonth(1);
        LocalDate fim = referencia.withDayOfMonth(referencia.lengthOfMonth());
        VisaoFinanceiraService.DetalheCompetencia detalhe =
                visaoFinanceiraService.detalheCompetencia(usuarioId, inicio, fim);
        return List.of(
                new Origem("ENTRADAS_COMPETENCIA", null, "Entradas por competência",
                        detalhe.entradas(),
                        new Navegacao(Navegacao.TRANSACOES, null, Map.of(
                                "inicio", inicio.toString(),
                                "fim", fim.toString(),
                                "tipo", TipoTransacao.ENTRADA.name()))),
                // Saidas nao cartao e consumo de cartao por competencia nao tem
                // filtro exato em /v1/transacoes/periodo: ficam informativas
                new Origem("SAIDAS_NAO_CARTAO_COMPETENCIA", null,
                        "Saídas não cartão por competência", detalhe.saidasNaoCartao().negate()),
                new Origem("CONSUMO_CARTAO_COMPETENCIA", null,
                        "Consumo de cartão por competência", detalhe.consumoCartao().negate()));
    }

    private List<Origem> variacaoPatrimonialOrigem(Long usuarioId, LocalDate referencia) {
        VariacaoPatrimonial variacao = variacaoPatrimonial(
                usuarioId, referencia.withDayOfMonth(1), referencia);
        return List.of(
                new Origem("VARIACAO_CAIXA", null, "Variação do caixa", variacao.caixa()),
                new Origem("VARIACAO_PASSIVO", null, "Variação do passivo", variacao.passivo().negate()),
                new Origem("APORTES_INVESTIMENTO", null,
                        "Aportes líquidos em investimentos", variacao.aportesInvestimento()));
    }

    private List<Origem> investidoOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>();
        ativoRepository.findByUsuarioId(usuarioId).stream()
                .filter(a -> a.getValorAtual() != null && a.getCotacaoEm() != null
                        && a.getQuantidade().signum() > 0)
                .forEach(a -> origens.add(new Origem("POSICAO", a.getId(), a.getTicker(),
                        a.getQuantidade().multiply(a.getValorAtual()),
                        Navegacao.para(Navegacao.INVESTIMENTO, a.getId()))));
        return origens;
    }

    private List<Origem> dividasOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>();
        for (Carteira c : carteiraRepository.findByUsuarioIdAndNatureza(
                usuarioId, NaturezaContaFinanceira.PASSIVO)) {
            if (c.getSaldo().signum() > 0) {
                origens.add(new Origem("CONTA_FINANCEIRA", c.getId(), c.getNome(), c.getSaldo(),
                        Navegacao.para(Navegacao.EXTRATO_CONTA, c.getId())));
            }
        }
        return origens;
    }

    private List<Origem> patrimonioOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>(contasOrigem(usuarioId, NaturezaContaFinanceira.ATIVO, false));
        origens.addAll(investidoOrigem(usuarioId));
        for (Carteira passivo : carteiraRepository.findByUsuarioIdAndNatureza(
                usuarioId, NaturezaContaFinanceira.PASSIVO)) {
            origens.add(new Origem("CONTA_FINANCEIRA", passivo.getId(), passivo.getNome(),
                    passivo.getSaldo().negate(),
                    Navegacao.para(Navegacao.EXTRATO_CONTA, passivo.getId())));
        }
        return origens;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
