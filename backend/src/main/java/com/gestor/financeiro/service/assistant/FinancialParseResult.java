package com.gestor.financeiro.service.assistant;

public record FinancialParseResult(ParseOutcome outcome, TransactionDraftV1 draft, String question) {
    public static FinancialParseResult notFinancial() {
        return new FinancialParseResult(ParseOutcome.NOT_FINANCIAL, null, null);
    }
}
