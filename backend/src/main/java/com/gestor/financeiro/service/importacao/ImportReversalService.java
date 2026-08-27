package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.ImportRecordRepository;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.service.job.BackgroundJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Desfaz um lote já lançado.
 *
 * <p>Reversão aqui é <b>compensação</b>, no padrão do ADR-0009: cada transação importada é
 * cancelada pelo caminho de domínio ({@link TransacaoService#deletar(Long, Long, String)}), que
 * emite movimento {@code ESTORNO}, devolve o gasto da categoria e mantém a transação no histórico
 * como inativa. Nada é apagado, e o vínculo {@code import_records.transacao_id} é preservado — é o
 * que torna a reversão auditável.</p>
 *
 * <p>Idempotência: a chave {@code IMPORT_REVERSAL:{batchId}:{recordId}} vai ao ledger, e registro
 * já revertido é pulado. Reexecutar o job não estorna duas vezes.</p>
 */
@Service
public class ImportReversalService {

    private static final Logger log = LoggerFactory.getLogger(ImportReversalService.class);
    static final String TIPO_JOB_REVERSAO = "IMPORT_REVERSAL";
    private static final List<ImportRecordStatus> REVERSIVEIS = List.of(ImportRecordStatus.COMMITTED);

    private final ImportBatchService batches;
    private final ImportRecordRepository records;
    private final TransacaoService transacaoService;
    private final BackgroundJobService jobs;
    private final TransactionTemplate porRegistro;
    private final int tamanhoDoBloco;

    public ImportReversalService(ImportBatchService batches, ImportRecordRepository records,
                                 TransacaoService transacaoService, BackgroundJobService jobs,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${app.import.commit.chunk-size:200}") int tamanhoDoBloco) {
        this.batches = batches;
        this.records = records;
        this.transacaoService = transacaoService;
        this.jobs = jobs;
        this.porRegistro = new TransactionTemplate(transactionManager);
        this.porRegistro.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tamanhoDoBloco = Math.max(1, tamanhoDoBloco);
    }

    /** Enfileira a reversão; o lote continua {@code COMMITTED} até o estorno terminar. */
    @Transactional
    public ImportBatch solicitarReversao(Long usuarioId, Long batchId) {
        ImportBatch batch = batches.get(usuarioId, batchId);
        if (batch.getStatus() == ImportBatchStatus.REVERSED) {
            return batch;
        }
        if (batch.getStatus() != ImportBatchStatus.COMMITTED) {
            throw new BusinessException("Só um lote lançado pode ser revertido");
        }
        jobs.enqueue(TIPO_JOB_REVERSAO + ":" + batchId, TIPO_JOB_REVERSAO,
                "{\"usuarioId\":" + usuarioId + ",\"batchId\":" + batchId + "}", (short) 1,
                20, Instant.now(), 3);
        return batch;
    }

    /** Executa o estorno registro a registro; reexecutar é seguro. */
    public void executar(Long usuarioId, Long batchId) {
        ImportBatch batch = batches.get(usuarioId, batchId);
        if (batch.getStatus() == ImportBatchStatus.REVERSED) return;
        if (batch.getStatus() != ImportBatchStatus.COMMITTED) {
            throw new BusinessException("Só um lote lançado pode ser revertido");
        }

        int cursor = 0;
        int revertidos = 0;
        while (true) {
            List<ImportRecord> bloco = records.paginaParaLancamento(batchId, cursor, REVERSIVEIS,
                    Limit.of(tamanhoDoBloco));
            if (bloco.isEmpty()) break;
            for (ImportRecord record : bloco) {
                cursor = record.getSourceLine();
                reverterRegistro(usuarioId, batchId, record.getId());
                revertidos++;
            }
        }

        batches.transition(usuarioId, batchId, ImportBatchStatus.REVERSED, null);
        log.info("Importação {} revertida: {} registro(s) estornado(s)", batchId, revertidos);
    }

    private void reverterRegistro(Long usuarioId, Long batchId, Long registroId) {
        porRegistro.executeWithoutResult(status -> {
            ImportRecord record = records.findById(registroId).orElseThrow();
            if (record.getStatus() != ImportRecordStatus.COMMITTED || record.getTransacao() == null) {
                return;
            }
            // Estorno idempotente: a mesma chave no ledger devolve o movimento existente.
            transacaoService.deletar(record.getTransacao().getId(), usuarioId,
                    "IMPORT_REVERSAL:" + batchId + ":" + record.getId());
            record.setStatus(ImportRecordStatus.REVERSED);
            records.save(record);
        });
    }
}
