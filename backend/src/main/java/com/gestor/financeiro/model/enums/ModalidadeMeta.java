package com.gestor.financeiro.model.enums;

/**
 * Modalidade da meta (ADR-0012, PR-F2-12) — exatamente uma por meta.
 * COFRE_REAL: reserva e transferencia real para a conta COFRE da meta.
 * RESERVA_VIRTUAL: alocacao explicita sobre uma conta de caixa, sem lancamento
 * no ledger; o dinheiro continua na conta e reduz apenas "Disponivel para
 * gastar" (ADR-0013).
 */
public enum ModalidadeMeta {
    COFRE_REAL,
    RESERVA_VIRTUAL
}
