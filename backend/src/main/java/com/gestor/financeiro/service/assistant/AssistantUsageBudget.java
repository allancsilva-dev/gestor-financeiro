package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Component
public class AssistantUsageBudget {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int userCalls;
    private final BigDecimal globalCost;
    private final BigDecimal estimatedCallCost;

    public AssistantUsageBudget(JdbcTemplate jdbc, Clock clock,
            @Value("${assistant.limits.external-calls-per-user-day:20}") int userCalls,
            @Value("${assistant.limits.global-cost-usd-per-day:5.00}") BigDecimal globalCost,
            @Value("${assistant.limits.estimated-call-cost-usd:0.01}") BigDecimal estimatedCallCost) {
        this.jdbc = jdbc; this.clock = clock; this.userCalls = userCalls;
        this.globalCost = globalCost; this.estimatedCallCost = estimatedCallCost;
    }

    /** Reserva antes da chamada; a linha global serializa também deployments com múltiplas instâncias. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reserve(Long usuarioId) {
        if (usuarioId == null) throw new IllegalArgumentException("Titular obrigatório para reservar orçamento de IA");
        LocalDate day = LocalDate.now(clock);
        jdbc.update("""
                insert into assistant_usage_daily(usuario_id, usage_date, external_calls, cost_usd)
                values (null, ?, 0, 0) on conflict (usuario_id, usage_date) do nothing
                """, day);
        BigDecimal spent = jdbc.queryForObject("""
                select cost_usd from assistant_usage_daily
                 where usuario_id is null and usage_date = ? for update
                """, BigDecimal.class, day);
        jdbc.update("""
                insert into assistant_usage_daily(usuario_id, usage_date, external_calls, cost_usd)
                values (?, ?, 0, 0) on conflict (usuario_id, usage_date) do nothing
                """, usuarioId, day);
        Integer calls = jdbc.queryForObject("""
                select external_calls from assistant_usage_daily
                 where usuario_id = ? and usage_date = ? for update
                """, Integer.class, usuarioId, day);
        if (calls == null || spent == null) throw new IllegalStateException("Contador diário do assistente indisponível");
        if (calls >= userCalls || spent.add(estimatedCallCost).compareTo(globalCost) > 0)
            throw new AssistantException("AI_BUDGET_EXCEEDED", "Limite diário do assistente atingido", HttpStatus.TOO_MANY_REQUESTS, 3600);
        jdbc.update("""
                update assistant_usage_daily
                   set external_calls = external_calls + 1, cost_usd = cost_usd + ?
                 where usuario_id is null and usage_date = ?
                """, estimatedCallCost, day);
        jdbc.update("""
                update assistant_usage_daily
                   set external_calls = external_calls + 1, cost_usd = cost_usd + ?
                 where usuario_id = ? and usage_date = ?
                """,
                estimatedCallCost, usuarioId, day);
    }
}
