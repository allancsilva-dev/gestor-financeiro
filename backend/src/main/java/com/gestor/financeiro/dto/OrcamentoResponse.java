package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrcamentoResponse(
    Long id,
    Integer mes,
    Integer ano,
    BigDecimal valorTotalPlanejado,
    BigDecimal valorTotalGasto,
    List<OrcamentoCategoriaResponse> categorias
) {}
