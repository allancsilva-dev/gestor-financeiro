package com.gestor.financeiro.model.enums;

/**
 * Subtipo da conta financeira unificada (ADR-0008).
 * CUSTODIA nao possui saldo monetario (saldo = 0 tecnico); CARTAO e a unica
 * de natureza PASSIVO nesta fase.
 */
public enum SubtipoContaFinanceira {
    DINHEIRO,
    CORRENTE,
    POUPANCA,
    PAGAMENTO,
    COFRE,
    CUSTODIA,
    CARTAO;

    public NaturezaContaFinanceira naturezaPadrao() {
        return this == CARTAO ? NaturezaContaFinanceira.PASSIVO : NaturezaContaFinanceira.ATIVO;
    }
}
