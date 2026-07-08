package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FaturaLancamentoDto(
    Long transacaoId,
    String descricao,
    BigDecimal valor,
    LocalDate data,
    Long categoriaId,
    String categoriaNome,
    String categoriaCor,
    String categoriaIcone,
    Integer parcelaAtual,
    Integer totalParcelas
) {}
