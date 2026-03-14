package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.enums.StatusPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ParcelaResponseDto(
    Long id,
    Long transacaoId,
    Integer numeroParcela,
    Integer totalParcelas,
    BigDecimal valor,
    LocalDate dataVencimento,
    StatusPagamento status,
    LocalDate dataPagamento
) {
    public static ParcelaResponseDto fromEntity(Parcela parcela) {
        return new ParcelaResponseDto(
            parcela.getId(),
            parcela.getTransacao() != null ? parcela.getTransacao().getId() : null,
            parcela.getNumeroParcela(),
            parcela.getTotalParcelas(),
            parcela.getValor(),
            parcela.getDataVencimento(),
            parcela.getStatus(),
            parcela.getDataPagamento()
        );
    }
}
