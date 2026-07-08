package com.gestor.financeiro.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MovimentacaoResponse {
    private Long id;
    private String tipo;
    private LocalDate data;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal valorTotal;
}
