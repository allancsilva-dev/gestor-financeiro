package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import com.gestor.financeiro.model.enums.ImportBatchStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByIdAndUsuarioId(Long id, Long usuarioId);
    Optional<ImportBatch> findByUsuarioIdAndIdempotencyKey(Long usuarioId, String idempotencyKey);

    /**
     * Lotes do titular ainda em processamento. A janela evita que um lote órfão (processo morto no
     * meio do parse) bloqueie o usuário para sempre.
     */
    long countByUsuarioIdAndStatusInAndCreatedAtAfter(Long usuarioId,
                                                      Collection<ImportBatchStatus> status,
                                                      Instant desde);
}
