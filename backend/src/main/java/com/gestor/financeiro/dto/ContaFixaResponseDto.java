package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.enums.StatusPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaFixaResponseDto(
    Long id,
    String nome,
    BigDecimal valorPlanejado,
    BigDecimal valorReal,
    Integer diaVencimento,
    LocalDate dataProximoVencimento,
    StatusPagamento status,
    Boolean recorrente,
    Boolean ativo,
    String observacoes,
    CategoriaResumoDto categoria
) {
    public static ContaFixaResponseDto fromEntity(ContaFixa contaFixa) {
        return new ContaFixaResponseDto(
            contaFixa.getId(),
            contaFixa.getNome(),
            contaFixa.getValorPlanejado(),
            contaFixa.getValorReal(),
            contaFixa.getDiaVencimento(),
            contaFixa.getDataProximoVencimento(),
            contaFixa.getStatus(),
            contaFixa.getRecorrente(),
            contaFixa.getAtivo(),
            contaFixa.getObservacoes(),
            CategoriaResumoDto.fromEntity(contaFixa.getCategoria())
        );
    }
}
