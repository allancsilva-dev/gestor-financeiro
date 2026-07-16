package com.gestor.financeiro.model.enums;

/**
 * Conciliacao da movimentacao de investimento (ADR-0011, PR-F2-13).
 * CONCILIADA: operacao real com movimento de caixa vinculado.
 * EXTERNO: snapshot sem historico de caixa (legado/importacao); importacao
 * nunca inventa movimento de caixa ausente.
 */
public enum ConciliacaoInvestimento {
    CONCILIADA,
    EXTERNO
}
