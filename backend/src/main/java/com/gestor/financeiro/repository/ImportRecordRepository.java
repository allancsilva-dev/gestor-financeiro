package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Identidade forte: mesma instituição e mesmo id externo já lançado pelo titular. Instituição
     * vem do lote, porque o id externo (FITID) só é único dentro da instituição.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImportRecord r set r.status = com.gestor.financeiro.model.enums.ImportRecordStatus.DUPLICATE
             where r.batch.id = :batchId
               and r.externalId is not null
               and r.status in (com.gestor.financeiro.model.enums.ImportRecordStatus.VALID,
                                com.gestor.financeiro.model.enums.ImportRecordStatus.PENDING_REVIEW)
               and exists (
                   select 1 from ImportRecord anterior
                    where anterior.batch.usuario.id = :usuarioId
                      and anterior.batch.id <> :batchId
                      and anterior.status = com.gestor.financeiro.model.enums.ImportRecordStatus.COMMITTED
                      and anterior.externalId = r.externalId
                      and ((anterior.batch.institutionCode is null and r.batch.institutionCode is null)
                           or anterior.batch.institutionCode = r.batch.institutionCode))
            """)
    int marcarDuplicadosPorIdentidadeExterna(@Param("usuarioId") Long usuarioId,
                                             @Param("batchId") Long batchId);

    /**
     * Heurística explicável: mesma impressão digital de um registro já lançado. Só toca em registro
     * VALID — o que já está em revisão continua na mão do usuário — e nunca vira constraint, porque
     * dois lançamentos idênticos no mesmo dia podem ser dois fatos reais.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImportRecord r set r.status = com.gestor.financeiro.model.enums.ImportRecordStatus.DUPLICATE
             where r.batch.id = :batchId
               and r.status = com.gestor.financeiro.model.enums.ImportRecordStatus.VALID
               and exists (
                   select 1 from ImportRecord anterior
                    where anterior.batch.usuario.id = :usuarioId
                      and anterior.batch.id <> :batchId
                      and anterior.status = com.gestor.financeiro.model.enums.ImportRecordStatus.COMMITTED
                      and anterior.recordFingerprint = r.recordFingerprint)
            """)
    int marcarDuplicadosPorImpressao(@Param("usuarioId") Long usuarioId,
                                     @Param("batchId") Long batchId);
}
