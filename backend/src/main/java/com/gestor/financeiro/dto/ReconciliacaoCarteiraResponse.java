package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record ReconciliacaoCarteiraResponse(
        Long carteiraId,
        Long usuarioId,
        BigDecimal saldoMaterializado,
        BigDecimal saldoLedger,
        BigDecimal diferenca,
        Status status
) {

    public enum Status {
        OK,
        DIVERGENTE
    }
}
