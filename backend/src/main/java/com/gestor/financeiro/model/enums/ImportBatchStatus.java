package com.gestor.financeiro.model.enums;

public enum ImportBatchStatus {
    RECEIVED,
    PARSED,
    PENDING_REVIEW,
    READY_TO_COMMIT,
    COMMITTING,
    COMMITTED,
    FAILED,
    REVERSED
}
