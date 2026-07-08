package com.gestor.financeiro.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AtivoRequest {
    private String ticker;
    private String nome;
    private String tipo;
    private BigDecimal valorAtual;
}
