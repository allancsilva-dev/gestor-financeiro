package com.gestor.financeiro.model.enums;

public enum ImportRecordReasonCode {
    DATE_MISSING, DATE_INVALID, DATE_AMBIGUOUS,
    AMOUNT_MISSING, AMOUNT_INVALID, AMOUNT_AMBIGUOUS, AMOUNT_ROUNDING_REQUIRED,
    CURRENCY_MISSING, CURRENCY_INVALID, CURRENCY_UNSUPPORTED,
    DIRECTION_MISSING, DIRECTION_INVALID, DIRECTION_CONFLICT,
    DESCRIPTION_MISSING, DESCRIPTION_INVALID,
    EXTERNAL_ID_INVALID, COMMIT_FAILED, MULTIPLE_ISSUES,
    /** Duplicado contra lote do titular que ainda espera revisão (Fase 6, ADR-0021). */
    DUPLICATE_PENDING_BATCH,
    /** Duplicado contra registro que o titular já reverteu de propósito (Fase 6, ADR-0021). */
    DUPLICATE_REVERSED
}
