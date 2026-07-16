package com.gestor.financeiro.dto;

import java.time.Instant;
import java.util.List;

public record ReconciliacaoGlobalResponse(
        Status status,
        Instant executadoEm,
        long verificacoes,
        long divergencias,
        List<ResumoInvariante> resumo,
        List<Divergencia> detalhes
) {
    public enum Status { OK, DIVERGENTE }

    public enum Invariante {
        SALDO_LEDGER,
        PASSIVO_FATURAS,
        COFRE_META,
        TRANSACAO_INCOMPLETA
    }

    public record ResumoInvariante(
            Invariante invariante,
            long verificacoes,
            long aprovadas,
            long divergencias
    ) {}

    public record Divergencia(
            Invariante invariante,
            String recurso,
            Long recursoId,
            String esperado,
            String encontrado
    ) {}
}
