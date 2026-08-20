package com.gestor.financeiro.service;

import com.gestor.financeiro.dto.CartaoResumoDto;
import com.gestor.financeiro.dto.CategoriaResumoDto;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.repository.ContaFixaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compromissos proximos (PR-F3-01): faturas e parcelas que compoem exatamente
 * a metrica oficial Comprometido (calculo compartilhado com MetricasService,
 * nunca duplicado) mais contas fixas como PREVISTO, fora do total. Item de
 * conta fixa pode carregar alerta FALHA_SALDO da recorrencia, dispensando
 * request separado na home.
 */
@Service
public class CompromissosService {

    /** Horizonte maximo aceito a partir da data de referencia. */
    private static final int HORIZONTE_MAXIMO_MESES = 12;

    public static final String GRUPO_COMPROMETIDO = "COMPROMETIDO";
    public static final String GRUPO_PREVISTO = "PREVISTO";
    public static final String ALERTA_FALHA_SALDO = "FALHA_SALDO";
    public static final String TIPO_CONTA_FIXA = "CONTA_FIXA";

    private final MetricasService metricasService;
    private final ContaFixaService contaFixaService;
    private final ContaFixaRepository contaFixaRepository;
    private final Clock clock;

    public CompromissosService(MetricasService metricasService,
                               ContaFixaService contaFixaService,
                               ContaFixaRepository contaFixaRepository,
                               Clock clock) {
        this.metricasService = metricasService;
        this.contaFixaService = contaFixaService;
        this.contaFixaRepository = contaFixaRepository;
        this.clock = clock;
    }

    public record CompromissoItem(
            String tipo,        // FATURA | PARCELA | CONTA_FIXA
            Long id,
            String descricao,
            BigDecimal valor,
            LocalDate vencimento,
            String grupo,       // COMPROMETIDO | PREVISTO
            String alerta,      // FALHA_SALDO | null
            // estruturados: a UI monta "iPhone 15 (6/10)" e "Visa .... 8034"
            // sem parsear a descricao. Nulos em CONTA_FIXA.
            Integer numeroParcela,
            Integer totalParcelas,
            CartaoResumoDto cartao,
            CategoriaResumoDto categoria
    ) {
    }

    public record Compromissos(
            LocalDate referencia,
            LocalDate horizonte,
            BigDecimal totalComprometido,
            List<CompromissoItem> itens
    ) {
    }

    @Transactional(readOnly = true)
    public Compromissos listar(Long usuarioId, LocalDate ate) {
        LocalDate referencia = LocalDate.now(clock);
        LocalDate horizonte = ate != null
                ? ate
                : referencia.withDayOfMonth(referencia.lengthOfMonth());
        if (horizonte.isBefore(referencia)) {
            throw new BusinessException("Horizonte não pode ser anterior à data de referência");
        }
        if (horizonte.isAfter(referencia.plusMonths(HORIZONTE_MAXIMO_MESES))) {
            throw new BusinessException(
                    "Horizonte não pode passar de " + HORIZONTE_MAXIMO_MESES + " meses");
        }

        BigDecimal totalComprometido = metricasService.comprometido(usuarioId, horizonte);

        List<CompromissoItem> itens = new ArrayList<>();
        metricasService.obrigacoesComprometidas(usuarioId, horizonte).forEach(o ->
                itens.add(new CompromissoItem(o.tipo(), o.id(), o.descricao(), o.valor(),
                        o.vencimento(), GRUPO_COMPROMETIDO, null, o.numeroParcela(),
                        o.totalParcelas(), o.cartao(), o.categoria())));

        Set<Long> contasComFalha = contaFixaService.listarFalhasPendentes(usuarioId).stream()
                .map(e -> e.getContaFixa().getId())
                .collect(Collectors.toSet());
        contaFixaRepository.findPrevistasAteHorizonte(
                usuarioId, TipoTransacao.SAIDA, StatusPagamento.PAGO,
                StatusPagamento.CANCELADO, horizonte)
                .forEach(c -> itens.add(new CompromissoItem(TIPO_CONTA_FIXA, c.getId(),
                        c.getNome(), c.getValorPlanejado(), c.getDataProximoVencimento(),
                        GRUPO_PREVISTO,
                        contasComFalha.contains(c.getId()) ? ALERTA_FALHA_SALDO : null,
                        null, null, null, null)));

        itens.sort(Comparator
                .comparing(CompromissoItem::vencimento,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CompromissoItem::tipo)
                .thenComparing(CompromissoItem::id, Comparator.nullsLast(Comparator.naturalOrder())));

        return new Compromissos(referencia, horizonte, totalComprometido, itens);
    }
}
