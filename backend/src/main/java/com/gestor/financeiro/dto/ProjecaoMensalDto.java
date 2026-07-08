package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record ProjecaoMensalDto(
    String periodo,
    int mes,
    int ano,
    BigDecimal saldoInicial,
    BigDecimal totalContasFixas,
    BigDecimal totalParcelas,
    BigDecimal totalFaturas,
    BigDecimal totalSaidas,
    BigDecimal saldoFinal,
    boolean saldoRealizado
) {}
