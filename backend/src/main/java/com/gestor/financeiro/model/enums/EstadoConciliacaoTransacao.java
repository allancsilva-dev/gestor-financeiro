package com.gestor.financeiro.model.enums;

/**
 * Estado de conciliacao da transacao (PR-F2-05, ADR-0009/0013).
 * PENDENTE_CONCILIACAO e permitido apenas para legado e importacao incompleta;
 * nunca entra em saldos ou metricas conciliadas. Operacao manual de caixa nova
 * sem conta financeira e rejeitada (422) em vez de ficar pendente.
 */
public enum EstadoConciliacaoTransacao {
    CONCILIADA,
    PENDENTE_CONCILIACAO
}
