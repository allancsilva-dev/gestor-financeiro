package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.enums.FaturaStatus;
import com.gestor.financeiro.model.enums.ModalidadeMeta;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.TipoConta;
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

        // Comprometido: vencidas nao pagas + vencimento ate o horizonte; fatura ou
        // parcela distante nao entra so por estar aberta (ADR-0013)
        BigDecimal comprometidoFaturas = faturaCartaoRepository.somarSaldoRestanteNoPeriodo(
                usuarioId, FaturaStatus.PAGA, INICIO_OBRIGACOES, horizonte);
        BigDecimal comprometidoParcelas = parcelaRepository.somarValorNoPeriodo(
                usuarioId, INICIO_OBRIGACOES, horizonte,
                StatusPagamento.PAGO, TipoTransacao.SAIDA, TipoConta.CREDITO);
        BigDecimal comprometido = comprometidoFaturas.add(comprometidoParcelas);

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

    // --- Drill-down (PR-F2-16): origem de cada numero ---

    public record Origem(String tipo, Long id, String descricao, BigDecimal valor) {
    }

    public List<Origem> origens(Long usuarioId, String metrica) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate horizonte = hoje.withDayOfMonth(hoje.lengthOfMonth());
        return switch (metrica.toUpperCase()) {
            case "DISPONIVEL_AGORA" -> contasOrigem(usuarioId, NaturezaContaFinanceira.ATIVO, true);
            case "RESERVADO" -> reservadoOrigem(usuarioId);
            case "COMPROMETIDO" -> comprometidoOrigem(usuarioId, horizonte);
            case "INVESTIDO" -> investidoOrigem(usuarioId);
            case "DIVIDAS" -> dividasOrigem(usuarioId);
            case "PATRIMONIO_LIQUIDO" -> patrimonioOrigem(usuarioId);
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
            origens.add(new Origem("CONTA_FINANCEIRA", c.getId(), c.getNome(), c.getSaldo()));
        }
        return origens;
    }

    private List<Origem> reservadoOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>();
        for (Carteira cofre : carteiraRepository.findByUsuarioIdAndSubtipo(
                usuarioId, SubtipoContaFinanceira.COFRE)) {
            origens.add(new Origem("COFRE", cofre.getId(), cofre.getNome(), cofre.getSaldo()));
        }
        for (Meta meta : metaRepository.findByUsuarioIdAndModalidade(usuarioId, ModalidadeMeta.RESERVA_VIRTUAL)) {
            if (meta.getValorReservado() != null && meta.getValorReservado().signum() > 0) {
                origens.add(new Origem("ALOCACAO_VIRTUAL", meta.getId(),
                        "Meta: " + meta.getNome(), meta.getValorReservado()));
            }
        }
        return origens;
    }

    private List<Origem> comprometidoOrigem(Long usuarioId, LocalDate horizonte) {
        List<Origem> origens = new ArrayList<>();
        faturaCartaoRepository.findByUsuarioId(usuarioId).stream()
                .filter(f -> f.getStatus() != FaturaStatus.PAGA)
                .filter(f -> f.getDataVencimento() != null && !f.getDataVencimento().isAfter(horizonte))
                .forEach(f -> {
                    BigDecimal restante = nvl(f.getValorTotal()).subtract(nvl(f.getValorPago()));
                    if (restante.signum() > 0) {
                        origens.add(new Origem("FATURA", f.getId(),
                                "Fatura " + f.getMes() + "/" + f.getAno(), restante));
                    }
                });
        parcelaRepository.findFuturasByUsuarioId(usuarioId, INICIO_OBRIGACOES, StatusPagamento.PAGO)
                .stream()
                .filter(p -> !p.getDataVencimento().isAfter(horizonte))
                .forEach(p -> origens.add(new Origem("PARCELA", p.getId(),
                        p.getTransacao().getDescricao() + " " + p.getNumeroParcela() + "/"
                                + p.getTotalParcelas(), p.getValor())));
        return origens;
    }

    private List<Origem> investidoOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>();
        ativoRepository.findByUsuarioId(usuarioId).stream()
                .filter(a -> a.getValorAtual() != null && a.getCotacaoEm() != null
                        && a.getQuantidade().signum() > 0)
                .forEach(a -> origens.add(new Origem("POSICAO", a.getId(), a.getTicker(),
                        a.getQuantidade().multiply(a.getValorAtual()))));
        return origens;
    }

    private List<Origem> dividasOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>();
        for (Carteira c : carteiraRepository.findByUsuarioIdAndNatureza(
                usuarioId, NaturezaContaFinanceira.PASSIVO)) {
            if (c.getSaldo().signum() > 0) {
                origens.add(new Origem("CONTA_FINANCEIRA", c.getId(), c.getNome(), c.getSaldo()));
            }
        }
        return origens;
    }

    private List<Origem> patrimonioOrigem(Long usuarioId) {
        List<Origem> origens = new ArrayList<>(contasOrigem(usuarioId, NaturezaContaFinanceira.ATIVO, false));
        origens.addAll(investidoOrigem(usuarioId));
        for (Origem divida : dividasOrigem(usuarioId)) {
            origens.add(new Origem(divida.tipo(), divida.id(), divida.descricao(),
                    divida.valor().negate()));
        }
        return origens;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
