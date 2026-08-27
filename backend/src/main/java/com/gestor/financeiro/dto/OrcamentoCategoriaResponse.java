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
    Integer percentualGasto,
    /** O que veio do mês anterior conforme a política; negativo quando o mês passado estourou. */
    BigDecimal carryIn,
    /** `valorLimite + carryIn` — é contra este número que o gasto do mês é medido. */
    BigDecimal valorDisponivel,
    String politicaRollover
) {}
