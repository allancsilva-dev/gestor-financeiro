package com.gestor.financeiro.model.enums;

public enum ImportFormat {
    UNKNOWN,
    CSV,
    OFX,
    /** Snapshot NDJSON produzido a partir de conector regulado (ADR-0019). */
    OPEN_FINANCE
}
