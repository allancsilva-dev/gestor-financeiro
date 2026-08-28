package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.service.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OfficialFinancialReadTool implements FinancialReadTool {
    private final MetricasService metrics;
    private final OrcamentoService budgets;
    private final MetaService goals;
    private final CompromissosService commitments;
    private final InvestimentoService investments;
    private final ReconciliacaoGlobalService reconciliation;
    private final Clock clock;

    public OfficialFinancialReadTool(MetricasService metrics, OrcamentoService budgets, MetaService goals,
            CompromissosService commitments, InvestimentoService investments,
            ReconciliacaoGlobalService reconciliation, Clock clock) {
        this.metrics = metrics; this.budgets = budgets; this.goals = goals; this.commitments = commitments;
        this.investments = investments; this.reconciliation = reconciliation; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialToolResult execute(Long usuarioId, ValidatedFinancialQuery query) {
        Map<String, Object> facts = new LinkedHashMap<>();
        var today = java.time.LocalDate.now(clock);
        var reference = query.to().isAfter(today) ? today : query.to();
        String route;
        switch (query.intent()) {
            case BALANCE -> {
                var m = metrics.calcular(usuarioId, reference, query.to());
                facts.put("disponivelAgora", m.disponivelAgora()); facts.put("disponivelParaGastar", m.disponivelParaGastar());
                facts.put("reservado", m.reservado()); facts.put("dividas", m.dividas()); facts.put("patrimonioLiquido", m.patrimonioLiquido());
                route = "/api/v1/metricas";
            }
            case SPENDING_BY_CATEGORY -> {
                var m = metrics.calcular(usuarioId, reference, query.to());
                facts.put("resultadoMensal", m.resultadoMensal());
                facts.put("observacao", "Abra a composição do resultado mensal para categorias e lançamentos.");
                route = "/api/v1/metricas/RESULTADO_MENSAL/origens";
            }
            case BUDGET -> {
                YearMonth ym = YearMonth.from(query.to()); var b = YearMonth.now(clock).equals(ym)
                        ? budgets.buscarOuCriarAtual(usuarioId)
                        : budgets.buscarPorMes(usuarioId, ym.getMonthValue(), ym.getYear());
                facts.put("planejado", b.valorTotalPlanejado()); facts.put("gasto", b.valorTotalGasto()); facts.put("categorias", b.categorias().size());
                route = "/api/v1/orcamentos";
            }
            case GOALS -> {
                var page = goals.listarPorUsuario(usuarioId, null, PageRequest.of(0, 100));
                BigDecimal target = page.stream().map(Meta::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal saved = page.stream().map(Meta::getValorReservado).reduce(BigDecimal.ZERO, BigDecimal::add);
                facts.put("metasAtivas", page.getTotalElements()); facts.put("objetivoTotal", target); facts.put("reservado", saved);
                route = "/api/v1/metas/minhas";
            }
            case INVOICES -> {
                var m = metrics.calcular(usuarioId, reference, query.to()); facts.put("dividas", m.dividas());
                facts.put("observacao", "O total usa passivos de faturas não pagas; abra Faturas para detalhar por cartão.");
                route = "/api/v1/faturas";
            }
            case COMMITMENTS -> {
                var c = commitments.listar(usuarioId, query.to()); facts.put("totalComprometido", c.totalComprometido());
                facts.put("quantidade", c.itens().size()); route = "/api/v1/compromissos";
            }
            case INVESTMENTS -> {
                var page = investments.listarAtivos(usuarioId, PageRequest.of(0, 100));
                BigDecimal market = page.stream().map(a -> a.getValorMercado() == null ? BigDecimal.ZERO : a.getValorMercado())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                facts.put("ativos", page.getTotalElements()); facts.put("valorMercadoCotado", market); route = "/api/v1/investimentos";
            }
            default -> throw new IllegalArgumentException("Intenção não suportada");
        }
        var check = reconciliation.reconciliarUsuario(usuarioId);
        boolean ok = check.status() == ReconciliacaoGlobalResponse.Status.OK;
        return new FinancialToolResult(query.intent(), query.from(), query.to(), clock.instant(), route, ok,
                ok ? null : "Há dados não reconciliados; estes valores não devem ser tratados como oficiais.", facts);
    }
}
