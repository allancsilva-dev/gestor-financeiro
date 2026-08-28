package com.gestor.financeiro.service.assistant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record FinancialToolResult(ValidatedFinancialQuery.Intent intent, LocalDate competenceFrom,
                                  LocalDate competenceTo, Instant updatedAt, String sourceRoute,
                                  boolean reconciled, String caveat, Map<String, Object> facts) {
    public FinancialToolResult {
        facts = Map.copyOf(facts);
    }
}
