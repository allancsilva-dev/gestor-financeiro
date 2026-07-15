package com.gestor.financeiro.model.enums;

/**
 * Origem dos dados da conta financeira (ADR-0008): fonte externa nova sempre
 * entra pelo pipeline canonico de importacao/conciliacao.
 */
public enum OrigemDadosConta {
    MANUAL,
    CSV,
    OFX,
    INTEGRACAO,
    AJUSTE
}
