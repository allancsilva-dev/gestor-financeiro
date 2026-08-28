package com.gestor.financeiro.service.assistant;

public interface FinancialReadTool {
    FinancialToolResult execute(Long usuarioId, ValidatedFinancialQuery query);
}
