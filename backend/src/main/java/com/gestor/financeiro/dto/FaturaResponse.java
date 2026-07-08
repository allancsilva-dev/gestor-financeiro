package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FaturaResponse(
    Long id,
    Long contaId,
    String contaNome,
    Integer mes,
    Integer ano,
    LocalDate dataFechamento,
    LocalDate dataVencimento,
    BigDecimal valorTotal,
    BigDecimal valorPago,
    String status,
    LocalDate dataPagamento,
    List<FaturaLancamentoDto> lancamentos
) {}
