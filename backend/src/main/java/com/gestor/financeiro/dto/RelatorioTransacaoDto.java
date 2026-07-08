package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RelatorioTransacaoDto(
    Long id,
    String descricao,
    BigDecimal valor,
    LocalDate data,
    String categoriaNome,
    String categoriaCor
) {}
