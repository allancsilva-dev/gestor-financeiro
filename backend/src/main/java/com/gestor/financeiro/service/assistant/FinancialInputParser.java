package com.gestor.financeiro.service.assistant;

public interface FinancialInputParser {
    FinancialParseResult parse(Long usuarioId, String text);
}
