package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantUsageBudgetTest {
    @Test
    void reservaAtualizaContadorGlobalEIndividualAntesDaChamada() {
        FakeJdbc jdbc = new FakeJdbc();
        AssistantUsageBudget budget = budget(jdbc, 20, "5.00", "0.25");

        budget.reserve(7L);

        assertThat(jdbc.globalCalls).isEqualTo(1);
        assertThat(jdbc.userCalls).isEqualTo(1);
        assertThat(jdbc.globalCost).isEqualByComparingTo("0.25");
        assertThat(jdbc.userCost).isEqualByComparingTo("0.25");
    }

    @Test
    void tetoGlobalRecusaSemDebitarTitular() {
        FakeJdbc jdbc = new FakeJdbc(); jdbc.globalCost = new BigDecimal("4.90");
        AssistantUsageBudget budget = budget(jdbc, 20, "5.00", "0.25");

        assertThatThrownBy(() -> budget.reserve(7L))
                .isInstanceOfSatisfying(AssistantException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_BUDGET_EXCEEDED"));
        assertThat(jdbc.globalCalls).isZero();
        assertThat(jdbc.userCalls).isZero();
    }

    private AssistantUsageBudget budget(FakeJdbc jdbc, int calls, String global, String estimate) {
        return new AssistantUsageBudget(jdbc,
                Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC),
                calls, new BigDecimal(global), new BigDecimal(estimate));
    }

    private static final class FakeJdbc extends JdbcTemplate {
        private int globalCalls; private int userCalls;
        private BigDecimal globalCost = BigDecimal.ZERO; private BigDecimal userCost = BigDecimal.ZERO;

        @Override public int update(String sql, Object... args) {
            if (sql.stripLeading().startsWith("insert")) return 1;
            BigDecimal amount = (BigDecimal) args[0];
            if (sql.contains("usuario_id is null")) { globalCalls++; globalCost = globalCost.add(amount); }
            else { userCalls++; userCost = userCost.add(amount); }
            return 1;
        }

        @Override @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            return (T) (sql.contains("cost_usd") ? globalCost : Integer.valueOf(userCalls));
        }
    }
}
