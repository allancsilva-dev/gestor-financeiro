package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.ImportRecordRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public final class CanonicalImportOrchestrator {
    private final ImportBatchService batches;
    private final ImportConnectorRegistry connectors;
    private final ImportRecordRepository records;
    private final ImportBatchRepository batchRepository;
    private final ImportLimits limits;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    public CanonicalImportOrchestrator(ImportBatchService batches, ImportConnectorRegistry connectors,
                                       ImportRecordRepository records, ImportBatchRepository batchRepository,
                                       ImportLimits limits, EntityManager entityManager,
                                       PlatformTransactionManager transactionManager) {
        this.batches = batches; this.connectors = connectors; this.records = records;
        this.batchRepository = batchRepository; this.limits = limits; this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ImportBatch stage(Long usuarioId, ImportSource source, String idempotencyKey) throws IOException {
        String expectedHash = source.sha256();
        String initialHash = expectedHash == null ? sha256(source) : expectedHash;
        ImportBatch created = batches.create(usuarioId, ImportFormat.UNKNOWN, null, initialHash, idempotencyKey);
        if (created.getStatus() != ImportBatchStatus.RECEIVED || created.getFormat() != ImportFormat.UNKNOWN) return created;
        try {
            String actualHash = sha256(source);
            if (!actualHash.equals(initialHash))
                throw new ImportParsingException(ImportFailureCode.HASH_MISMATCH, "Hash do arquivo não confere");
            ImportConnectorRegistry.DetectedConnector detected = connectors.detect(source);
            batches.setDetected(usuarioId, created.getId(), detected.detection().format(), detected.detection().institutionCode());
            transactions.executeWithoutResult(status -> {
                try { parseTransaction(usuarioId, created.getId(), source, detected.connector()); }
                catch (IOException e) { throw new ParsingRuntimeException(e); }
            });
            return batches.get(usuarioId, created.getId());
        } catch (Exception failure) {
            ImportFailureCode code = failureCode(failure);
            transactions.executeWithoutResult(status -> {
                ImportBatch current = batchRepository.findByIdAndUsuarioId(created.getId(), usuarioId).orElse(null);
                if (current != null && current.getStatus() != ImportBatchStatus.FAILED)
                    batches.transition(usuarioId, created.getId(), ImportBatchStatus.FAILED, code);
            });
            if (failure instanceof IOException io) throw io;
            if (failure.getCause() instanceof IOException io) throw io;
            throw new ImportParsingException(code, "Falha ao processar importação", failure);
        }
    }

    private void parseTransaction(Long usuarioId, Long batchId, ImportSource source,
                                  FinancialDataConnector connector) throws IOException {
        ImportBatch batch = batchRepository.findByIdAndUsuarioId(batchId, usuarioId).orElseThrow();
        int[] counts = new int[3];
        connector.parse(source, canonical -> {
            ImportRecord record = new ImportRecord();
            record.setBatch(entityManager.getReference(ImportBatch.class, batchId));
            record.setSourceLine(canonical.sourceLine()); record.setExternalId(canonical.externalId());
            record.setRecordFingerprint(canonical.fingerprint()); record.setOccurredOn(canonical.occurredOn());
            record.setNormalizedDescription(canonical.description()); record.setAmount(canonical.amount());
            record.setCurrency(canonical.currency()); record.setDirection(canonical.direction());
            record.setStatus(canonical.status()); record.setReasonCode(canonical.reasonCode() == null ? null : canonical.reasonCode().name());
            records.save(record);
            if (canonical.status() == ImportRecordStatus.VALID) counts[0]++;
            else if (canonical.status() == ImportRecordStatus.INVALID) counts[1]++; else counts[2]++;
            int total = counts[0] + counts[1] + counts[2];
            if (total % limits.stagingFlush() == 0) { records.flush(); entityManager.clear(); }
        });
        batch = batchRepository.findByIdAndUsuarioId(batchId, usuarioId).orElseThrow();
        batch.setTotalRecords(counts[0] + counts[1] + counts[2]); batch.setValidRecords(counts[0]);
        batch.setInvalidRecords(counts[1]); batch.setPendingReviewRecords(counts[2]);
        batch.setStatus(ImportBatchStatus.PARSED); batchRepository.save(batch);
    }

    private String sha256(ImportSource source) throws IOException {
        if (source.size() == 0) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
        if (source.size() > limits.fileBytes()) throw new ImportParsingException(ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long bytes = 0; byte[] buffer = new byte[8192]; int read;
            try (InputStream input = source.openStream()) {
                while ((read = input.read(buffer)) != -1) { bytes += read; if (bytes > limits.fileBytes()) throw new ImportParsingException(ImportFailureCode.FILE_LIMIT_EXCEEDED, "Arquivo excede limite"); digest.update(buffer, 0, read); }
            }
            if (bytes == 0) throw new ImportParsingException(ImportFailureCode.EMPTY_FILE, "Arquivo vazio");
            return HexFormat.of().formatHex(digest.digest());
        } catch (ImportParsingException e) { throw e; }
        catch (Exception e) { throw new IOException("Falha ao calcular hash", e); }
    }
    private ImportFailureCode failureCode(Throwable failure) {
        Throwable current = failure;
        while (current != null) { if (current instanceof ImportParsingException parsing) return parsing.code(); current = current.getCause(); }
        return ImportFailureCode.PARSE_FAILED;
    }
    private static final class ParsingRuntimeException extends RuntimeException { private ParsingRuntimeException(IOException cause) { super(cause); } }
}
