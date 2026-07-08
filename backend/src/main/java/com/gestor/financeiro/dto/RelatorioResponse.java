package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RelatorioResponse(
    LocalDate inicio,
    LocalDate fim,
    BigDecimal totalEntradas,
    BigDecimal totalSaidas,
    BigDecimal saldo,
    int totalTransacoes,
    List<RelatorioCategoriaDto> gastosPorCategoria,
    List<RelatorioTransacaoDto> maioresDespesas,
    List<RelatorioContaDto> gastosPorConta
) {}
