package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CronogramaItemResponse(
        Long id,
        Origem origem,
        Integer numero,
        Integer total,
        BigDecimal valor,
        LocalDate vencimento,
        Status status
) {
    public enum Origem { CARTAO, PARCELA }
    public enum Status { PENDENTE, PARCIAL, PAGO, ATRASADO, CANCELADO }
}
