package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByIdAndUsuarioId(Long id, Long usuarioId);
    Optional<ImportBatch> findByUsuarioIdAndIdempotencyKey(Long usuarioId, String idempotencyKey);
}
