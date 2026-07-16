package com.gestor.financeiro.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MovimentacaoRequest {
    private String tipo;
    private LocalDate data;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    // Opcional: se informado, a movimentacao debita/credita o caixa desta carteira via ledger.
    private Long carteiraId;

    /**
     * Snapshot externo (ADR-0011): registro sem movimento de caixa, marcado
     * nao conciliado. Sem esta flag, COMPRA/VENDA/DIVIDENDO exigem carteiraId.
     */
    private Boolean externa;
}
