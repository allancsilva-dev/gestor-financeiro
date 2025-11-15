package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record CategoriaUpdateRequest(
    String nome,
    String cor,
    String icone,
    BigDecimal valorEsperado
) {}