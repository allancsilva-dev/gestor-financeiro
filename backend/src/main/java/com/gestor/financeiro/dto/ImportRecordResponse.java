package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.ImportRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha do arquivo já normalizada, como o usuário revisa antes de confirmar o lançamento. */
public record ImportRecordResponse(
        Long id,
        int sourceLine,
        String externalId,
        LocalDate occurredOn,
        String description,
        BigDecimal amount,
        String currency,
        String direction,
        String status,
        String reasonCode,
        Long transacaoId,
        /** Categoria aplicada por regra do titular ou escolhida na revisão. */
        Long categoriaId,
        String categoriaNome
) {
    public static ImportRecordResponse de(ImportRecord record) {
        return new ImportRecordResponse(
                record.getId(),
                record.getSourceLine(),
                record.getExternalId(),
                record.getOccurredOn(),
                record.getNormalizedDescription(),
                record.getAmount(),
                record.getCurrency(),
                record.getDirection() == null ? null : record.getDirection().name(),
                record.getStatus().name(),
                record.getReasonCode(),
                record.getTransacao() == null ? null : record.getTransacao().getId(),
                record.getCategoria() == null ? null : record.getCategoria().getId(),
                record.getCategoria() == null ? null : record.getCategoria().getNome());
    }
}
