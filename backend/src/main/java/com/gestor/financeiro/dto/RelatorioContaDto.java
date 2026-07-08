package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record RelatorioContaDto(
    Long contaId,
    String nome,
    String tipo,
    BigDecimal valorTotal,
    BigDecimal porcentagem
) {}
