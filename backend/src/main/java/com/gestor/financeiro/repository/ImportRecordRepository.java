package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    Optional<ImportRecord> findByIdAndBatchId(Long id, Long batchId);

    /** Fila de lançamento: anda por cursor de linha, para lote grande não virar OFFSET. */
    @Query("""
            select r from ImportRecord r
             where r.batch.id = :batchId
               and r.sourceLine > :aposLinha
               and r.status in :status
             order by r.sourceLine asc
            """)
    List<ImportRecord> paginaParaLancamento(@Param("batchId") Long batchId,
                                            @Param("aposLinha") int aposLinha,
                                            @Param("status") List<ImportRecordStatus> status,
                                            Limit limite);

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
                      and ((anterior.batch.instituicao is not null and r.batch.instituicao is not null
                            and anterior.batch.instituicao.id = r.batch.instituicao.id)
                           or ((anterior.batch.instituicao is null or r.batch.instituicao is null)
                               and ((anterior.batch.institutionCode is null and r.batch.institutionCode is null)
                                    or anterior.batch.institutionCode = r.batch.institutionCode))))
            """)
    int marcarDuplicadosPorIdentidadeExterna(@Param("usuarioId") Long usuarioId,
                                             @Param("batchId") Long batchId);

    /**
     * Identidade forte contra o que o titular já reverteu de propósito.
     *
     * <p>Sem isto, reverter um lote errado e sincronizar de novo traria tudo de volta como válido:
     * a sobreposição de janela desfaria silenciosamente uma decisão explícita. O registro é marcado
     * com motivo próprio para que reincluí-lo seja escolha consciente, nunca automática.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImportRecord r
               set r.status = com.gestor.financeiro.model.enums.ImportRecordStatus.DUPLICATE,
                   r.reasonCode = 'DUPLICATE_REVERSED'
             where r.batch.id = :batchId
               and r.externalId is not null
               and r.status in (com.gestor.financeiro.model.enums.ImportRecordStatus.VALID,
                                com.gestor.financeiro.model.enums.ImportRecordStatus.PENDING_REVIEW)
               and exists (
                   select 1 from ImportRecord anterior
                    where anterior.batch.usuario.id = :usuarioId
                      and anterior.batch.id <> :batchId
                      and anterior.status = com.gestor.financeiro.model.enums.ImportRecordStatus.REVERSED
                      and anterior.externalId = r.externalId
                      and ((anterior.batch.instituicao is not null and r.batch.instituicao is not null
                            and anterior.batch.instituicao.id = r.batch.instituicao.id)
                           or ((anterior.batch.instituicao is null or r.batch.instituicao is null)
                               and ((anterior.batch.institutionCode is null and r.batch.institutionCode is null)
                                    or anterior.batch.institutionCode = r.batch.institutionCode))))
            """)
    int marcarDuplicadosPorIdentidadeRevertida(@Param("usuarioId") Long usuarioId,
                                               @Param("batchId") Long batchId);

    /**
     * Identidade forte contra lote que ainda espera revisão.
     *
     * <p>A dedup original só comparava contra o que já virou lançamento. Com sincronização
     * automática e sobreposição de janela, e com o commit automático desligado por padrão, nada do
     * lote anterior chega a COMMITTED — cada ciclo recriaria os mesmos dias como registros novos,
     * indefinidamente, até o titular revisar. Vira enxurrada e a pessoa abandona o recurso.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImportRecord r
               set r.status = com.gestor.financeiro.model.enums.ImportRecordStatus.DUPLICATE,
                   r.reasonCode = 'DUPLICATE_PENDING_BATCH'
             where r.batch.id = :batchId
               and r.externalId is not null
               and r.status in (com.gestor.financeiro.model.enums.ImportRecordStatus.VALID,
                                com.gestor.financeiro.model.enums.ImportRecordStatus.PENDING_REVIEW)
               and exists (
                   select 1 from ImportRecord anterior
                    where anterior.batch.usuario.id = :usuarioId
                      and anterior.batch.id <> :batchId
                      and anterior.status in (com.gestor.financeiro.model.enums.ImportRecordStatus.VALID,
                                              com.gestor.financeiro.model.enums.ImportRecordStatus.PENDING_REVIEW,
                                              com.gestor.financeiro.model.enums.ImportRecordStatus.APPROVED)
                      and anterior.externalId = r.externalId
                      and ((anterior.batch.instituicao is not null and r.batch.instituicao is not null
                            and anterior.batch.instituicao.id = r.batch.instituicao.id)
                           or ((anterior.batch.instituicao is null or r.batch.instituicao is null)
                               and ((anterior.batch.institutionCode is null and r.batch.institutionCode is null)
                                    or anterior.batch.institutionCode = r.batch.institutionCode))))
            """)
    int marcarDuplicadosEmRevisao(@Param("usuarioId") Long usuarioId,
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
