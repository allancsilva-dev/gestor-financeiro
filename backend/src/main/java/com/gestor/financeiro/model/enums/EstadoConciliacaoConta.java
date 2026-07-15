package com.gestor.financeiro.model.enums;

/**
 * Estado de conciliacao da conta financeira (ADR-0008). Dados PENDENTE nao
 * entram em saldos/metricas conciliadas (ADR-0013).
 */
public enum EstadoConciliacaoConta {
    CONCILIADA,
    PENDENTE
}
