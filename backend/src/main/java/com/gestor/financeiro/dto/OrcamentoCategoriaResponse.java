package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record OrcamentoCategoriaResponse(
    Long id,
    Long categoriaId,
    String categoriaNome,
    String categoriaCor,
    String categoriaIcone,
    BigDecimal valorLimite,
    BigDecimal valorGasto,
    Integer percentualGasto
) {}
