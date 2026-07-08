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
}
