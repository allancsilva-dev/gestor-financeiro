package com.gestor.financeiro.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class AtivoResponse {
    private Long id;
    private String ticker;
    private String nome;
    private String tipo;
    private BigDecimal quantidade;
    private BigDecimal custoTotal;
    private BigDecimal valorAtual;
    private BigDecimal precoMedio;
    private BigDecimal lucroPrejuizo;
    private BigDecimal rentabilidade;
}
