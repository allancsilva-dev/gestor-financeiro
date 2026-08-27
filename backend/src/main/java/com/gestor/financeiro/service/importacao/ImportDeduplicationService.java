package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca registros já vistos antes, para reenviar o mesmo arquivo não duplicar o ledger.
 *
 * <p>Duas identidades, com pesos diferentes:</p>
 * <ul>
 *   <li><b>Identidade forte</b> — mesma instituição e mesmo {@code external_id} (FITID do OFX):
 *       é o mesmo fato bancário, sem margem de dúvida.</li>
 *   <li><b>Heurística</b> — mesmo {@code record_fingerprint} (data, valor, moeda, direção,
 *       descrição). Dois cafés iguais no mesmo dia são dois fatos reais, então isto <b>nunca</b>
 *       vira constraint: o registro é marcado {@code DUPLICATE} e quem decide é o usuário na
 *       prévia.</li>
 * </ul>
 *
 * <p>A comparação é contra registros já lançados ({@code COMMITTED}) do mesmo titular — o que
 * ainda está em revisão não é fato consumado.</p>
 */
@Service
public class ImportDeduplicationService {

    private final ImportRecordRepository records;
    private final ImportBatchRepository batches;

    public ImportDeduplicationService(ImportRecordRepository records, ImportBatchRepository batches) {
        this.records = records;
        this.batches = batches;
    }

    /** Marca duplicados do lote e devolve quantos foram marcados. */
    @Transactional
    public int marcarDuplicados(Long usuarioId, Long batchId) {
        int porIdentidade = records.marcarDuplicadosPorIdentidadeExterna(usuarioId, batchId);
        int porImpressao = records.marcarDuplicadosPorImpressao(usuarioId, batchId);
        int total = porIdentidade + porImpressao;

        if (total > 0) {
            // Contar ANTES de tocar na entidade. Cada consulta dispara auto-flush do contexto, e
            // aplicar os contadores um a um faria o flush gravar estado intermediário incoerente
            // (duplicados novos com válidos antigos), que o CHECK ck_import_batches_counts recusa.
            int duplicados = (int) records.countByBatchIdAndStatus(batchId, ImportRecordStatus.DUPLICATE);
            int validos = (int) records.countByBatchIdAndStatus(batchId, ImportRecordStatus.VALID);
            int emRevisao = (int) records.countByBatchIdAndStatus(batchId, ImportRecordStatus.PENDING_REVIEW);

            ImportBatch batch = batches.findByIdAndUsuarioId(batchId, usuarioId).orElseThrow();
            batch.setDuplicateRecords(duplicados);
            batch.setValidRecords(validos);
            batch.setPendingReviewRecords(emRevisao);
            batches.save(batch);
        }
        return total;
    }
}
