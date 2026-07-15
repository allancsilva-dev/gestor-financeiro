package com.gestor.financeiro.model.enums;

/**
 * Status da operacao financeira (ADR-0009). Operacao confirmada e imutavel;
 * correcao gera nova operacao de estorno e marca a original como ESTORNADA.
 */
public enum StatusOperacaoFinanceira {
    CONFIRMADA,
    ESTORNADA
}
