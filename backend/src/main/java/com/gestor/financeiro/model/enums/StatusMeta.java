package com.gestor.financeiro.model.enums;

/**
 * Ciclo de vida da meta (ADR-0004). Fonte canônica de estado; o boolean {@code ativa}
 * permanece sincronizado apenas por compatibilidade com clientes publicados.
 */
public enum StatusMeta {
    ATIVA,
    CONCLUIDA,
    ARQUIVADA
}
