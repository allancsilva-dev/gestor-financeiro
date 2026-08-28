package com.gestor.financeiro.service.assistant;

import java.time.LocalDate;

public record ValidatedFinancialQuery(Intent intent, LocalDate from, LocalDate to) {
    public enum Intent { BALANCE, SPENDING_BY_CATEGORY, BUDGET, GOALS, INVOICES, COMMITMENTS, INVESTMENTS }

    public ValidatedFinancialQuery {
        if (intent == null || from == null || to == null) throw new IllegalArgumentException("Consulta incompleta");
        if (to.isBefore(from) || to.isAfter(from.plusYears(1))) throw new IllegalArgumentException("Período inválido");
    }
}
