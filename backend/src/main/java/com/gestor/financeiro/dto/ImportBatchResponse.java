package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.ImportBatch;

import java.time.Instant;

/** Contrato público do lote de importação; nunca expõe entidade nem conteúdo do arquivo. */
public record ImportBatchResponse(
        Long id,
        String status,
        String format,
        String institutionCode,
        String fileSha256,
        int totalRecords,
        int validRecords,
        int invalidRecords,
        int pendingReviewRecords,
        int duplicateRecords,
        String failureCode,
        Instant createdAt,
        Instant updatedAt
) {
    public static ImportBatchResponse de(ImportBatch batch) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getStatus().name(),
                batch.getFormat().name(),
                batch.getInstitutionCode(),
                batch.getFileSha256(),
                batch.getTotalRecords(),
                batch.getValidRecords(),
                batch.getInvalidRecords(),
                batch.getPendingReviewRecords(),
                batch.getDuplicateRecords(),
                batch.getFailureCode(),
                batch.getCreatedAt(),
                batch.getUpdatedAt());
    }
}
