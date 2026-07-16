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

    /** Mapeamento deterministico do modelo legado (mesma regra do backfill V32/V35). */
    public static SubtipoContaFinanceira deTipoLegado(TipoCarteira tipo) {
        return switch (tipo) {
            case DINHEIRO -> DINHEIRO;
            case CONTA_BANCARIA -> CORRENTE;
            case POUPANCA -> POUPANCA;
            case CARTAO -> CARTAO;
        };
    }

    public NaturezaContaFinanceira naturezaPadrao() {
        return this == CARTAO ? NaturezaContaFinanceira.PASSIVO : NaturezaContaFinanceira.ATIVO;
    }
}
