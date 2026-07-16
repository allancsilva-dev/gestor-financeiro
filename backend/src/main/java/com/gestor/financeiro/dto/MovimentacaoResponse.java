package com.gestor.financeiro.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.gestor.financeiro.model.enums.ConciliacaoInvestimento;

@Data
@Builder
public class MovimentacaoResponse {
    private Long id;
    private String tipo;
    private LocalDate data;
    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal valorTotal;
    private ConciliacaoInvestimento conciliacao;
    private Long operacaoId;
}
