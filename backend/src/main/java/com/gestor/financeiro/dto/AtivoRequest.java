package com.gestor.financeiro.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AtivoRequest {
    private String ticker;
    private String nome;
    private String tipo;
    private BigDecimal valorAtual;
    /** Liquidez declarada (default IMEDIATA) — ADR-0011. */
    private String liquidez;
    /** Conta CUSTODIA opcional que agrupa a posicao — ADR-0011. */
    private Long custodiaId;
}
