package com.gestor.financeiro.model.enums;

/**
 * Liquidez declarada da conta financeira (ADR-0008). Somente contas com
 * liquidez IMEDIATA compoem "Disponivel agora" (ADR-0013).
 */
public enum LiquidezContaFinanceira {
    IMEDIATA,
    D1,
    D2,
    CARENCIA,
    BLOQUEADA
}
