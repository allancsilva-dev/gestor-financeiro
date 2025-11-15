package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record CategoriaCreateRequest(
    String nome,
    String cor,
    String icone,
    BigDecimal valorEsperado
) {}
