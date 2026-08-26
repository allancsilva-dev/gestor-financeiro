package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, Long> {

    /**
     * Página por cursor de {@code sourceLine}. Um lote chega a dezenas de milhares de linhas, então
     * a leitura anda pela chave e não por {@code OFFSET}.
     */
    @Query("""
            select r from ImportRecord r
             where r.batch.id = :batchId
               and r.sourceLine > :aposLinha
               and (:status is null or r.status = :status)
             order by r.sourceLine asc
            """)
    List<ImportRecord> pagina(@Param("batchId") Long batchId,
                              @Param("aposLinha") int aposLinha,
                              @Param("status") ImportRecordStatus status,
                              Limit limite);

    long countByBatchIdAndStatus(Long batchId, ImportRecordStatus status);
}
