package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record RelatorioCategoriaDto(
    Long categoriaId,
    String nome,
    String cor,
    String icone,
    BigDecimal valorTotal,
    BigDecimal porcentagem
) {}
