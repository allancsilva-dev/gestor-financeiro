package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.ImportRecord;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportOrigin;
import com.gestor.financeiro.model.enums.ImportRecordStatus;
import com.gestor.financeiro.model.enums.ImportBalanceReconciliation;
import com.gestor.financeiro.model.enums.TipoTransacao;
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
import java.math.BigDecimal;

@Service
public final class CanonicalImportOrchestrator {
    private final ImportBatchService batches;
    private final ImportConnectorRegistry connectors;
    private final ImportDeduplicationService deduplicacao;
    private final ImportCategorizacaoService categorizacao;
    private final ImportRecordRepository records;
    private final ImportBatchRepository batchRepository;
    private final ImportLimits limits;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    public CanonicalImportOrchestrator(ImportBatchService batches, ImportConnectorRegistry connectors,
                                       ImportDeduplicationService deduplicacao,
                                       ImportCategorizacaoService categorizacao,
                                       ImportRecordRepository records, ImportBatchRepository batchRepository,
                                       ImportLimits limits, EntityManager entityManager,
                                       PlatformTransactionManager transactionManager) {
        this.batches = batches; this.connectors = connectors; this.deduplicacao = deduplicacao; this.categorizacao = categorizacao; this.records = records;
        this.batchRepository = batchRepository; this.limits = limits; this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public ImportBatch stage(Long usuarioId, ImportSource source, String idempotencyKey) throws IOException {
        return stage(usuarioId, source, idempotencyKey, ImportMapping.automatico());
    }

    /** Com mapeamento do titular, o cabeçalho do arquivo deixa de precisar ser reconhecível. */
    public ImportBatch stage(Long usuarioId, ImportSource source, String idempotencyKey,
                             ImportMapping mapeamento) throws IOException {
        return stage(usuarioId, source, idempotencyKey, mapeamento, null, ImportOrigin.UPLOAD);
    }

    /**
     * Estágio com formato declarado pela origem.
     *
     * <p>{@code formatoDeclarado} nulo mantém o comportamento de sempre: detecção byte a byte, ou
     * CSV quando o titular trouxe mapeamento de colunas. Preenchido, pula a detecção — é o caminho
     * de quem já sabe o que está entregando e para quem competir por confiança contra os outros
     * conectores só produziria recusa por ambiguidade.</p>
     *
     * <p>{@code origem} viaja separada do formato de propósito (V68): um conector futuro pode
     * entregar CSV de verdade, e aí só a origem distingue sincronização de envio manual.</p>
     */
    public ImportBatch stage(Long usuarioId, ImportSource source, String idempotencyKey,
                             ImportMapping mapeamento, ImportFormat formatoDeclarado,
                             ImportOrigin origem) throws IOException {
        String declaredHash = source.sha256();
        String initialHash = declaredHash == null ? sha256(source) : declaredHash;
        ImportBatch created = batches.create(usuarioId, ImportFormat.UNKNOWN, null, initialHash, idempotencyKey, origem);
        if (created.getStatus() != ImportBatchStatus.RECEIVED || created.getFormat() != ImportFormat.UNKNOWN) return created;
        try {
            // Só relê o arquivo quando o hash veio declarado pelo chamador: sem declaração o
            // valor acima já é a leitura íntegra do arquivo e um segundo passe não prova nada.
            if (declaredHash != null && !sha256(source).equals(declaredHash))
                throw new ImportParsingException(ImportFailureCode.HASH_MISMATCH, "Hash do arquivo não confere");
            ImportConnectorRegistry.DetectedConnector detected = resolver(source, mapeamento, formatoDeclarado);
            batches.setDetected(usuarioId, created.getId(), detected.detection().format(), detected.detection().institutionCode());
            transactions.executeWithoutResult(status -> {
                try { parseTransaction(usuarioId, created.getId(), source, detected.connector(), mapeamento); }
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

    private ImportConnectorRegistry.DetectedConnector resolver(ImportSource source, ImportMapping mapeamento,
                                                               ImportFormat formatoDeclarado) throws IOException {
        if (formatoDeclarado != null) return comInstituicao(connectors.forFormat(formatoDeclarado), source);
        // Com mapeamento em mãos o titular já disse o que o arquivo é. Exigir que a detecção
        // reconheça o cabeçalho recusaria justamente o arquivo que o mapeamento existe para
        // resolver — mapeamento de coluna é conceito de CSV.
        if (mapeamento != null && !mapeamento.vazio()) return connectors.forFormat(ImportFormat.CSV);
        return connectors.detect(source);
    }

    /**
     * Formato veio declarado pela origem, mas a instituição só o conteúdo sabe.
     *
     * <p>Sem esse enriquecimento o lote fica com instituição nula, e a identidade forte da
     * deduplicação passa a casar dois bancos diferentes pelo id externo sozinho. Falha de detecção
     * aqui não é fatal: o formato já está decidido, e seguir sem instituição é pior que seguir com
     * ela, mas melhor que recusar o lote inteiro.</p>
     */
    private ImportConnectorRegistry.DetectedConnector comInstituicao(
            ImportConnectorRegistry.DetectedConnector escolhido, ImportSource source) {
        try {
            ConnectorDetection detalhe = escolhido.connector().detect(source);
            if (detalhe != null && detalhe.confidence() > 0
                    && detalhe.format() == escolhido.detection().format()) {
                return new ImportConnectorRegistry.DetectedConnector(escolhido.connector(), detalhe);
            }
        } catch (IOException | RuntimeException semDetalhe) {
            // Mapeamento do titular existe justamente para arquivos que a detecção não reconhece.
        }
        return escolhido;
    }

    private void parseTransaction(Long usuarioId, Long batchId, ImportSource source,
                                  FinancialDataConnector connector, ImportMapping mapeamento) throws IOException {
        ImportBatch batch = batchRepository.findByIdAndUsuarioId(batchId, usuarioId).orElseThrow();
        int[] counts = new int[3];
        BigDecimal[] movement = { BigDecimal.ZERO };
        ImportStatementBalances declared = connector.declaredBalances(source, mapeamento);
        connector.parse(source, mapeamento, canonical -> {
            ImportRecord record = new ImportRecord();
            record.setBatch(entityManager.getReference(ImportBatch.class, batchId));
            record.setSourceLine(canonical.sourceLine()); record.setExternalId(canonical.externalId());
            record.setRecordFingerprint(canonical.fingerprint()); record.setOccurredOn(canonical.occurredOn());
            record.setNormalizedDescription(canonical.description()); record.setAmount(canonical.amount());
            record.setCurrency(canonical.currency()); record.setDirection(canonical.direction());
            record.setStatus(canonical.status()); record.setReasonCode(canonical.reasonCode() == null ? null : canonical.reasonCode().name());
            records.save(record);
            if (canonical.amount() != null && canonical.direction() != null) {
                movement[0] = movement[0].add(canonical.direction() == TipoTransacao.SAIDA
                        ? canonical.amount().negate() : canonical.amount());
            }
            if (canonical.status() == ImportRecordStatus.VALID) counts[0]++;
            else if (canonical.status() == ImportRecordStatus.INVALID) counts[1]++; else counts[2]++;
            int total = counts[0] + counts[1] + counts[2];
            if (total % limits.stagingFlush() == 0) { records.flush(); entityManager.clear(); }
        });
        batch = batchRepository.findByIdAndUsuarioId(batchId, usuarioId).orElseThrow();
        batch.setTotalRecords(counts[0] + counts[1] + counts[2]); batch.setValidRecords(counts[0]);
        batch.setInvalidRecords(counts[1]); batch.setPendingReviewRecords(counts[2]);
        batch.setDeclaredOpeningBalance(declared.opening());
        batch.setDeclaredClosingBalance(declared.closing());
        batch.setDeclaredMovementTotal(movement[0]);
        if (declared.opening() == null || declared.closing() == null) {
            batch.setBalanceReconciliation(ImportBalanceReconciliation.UNAVAILABLE);
        } else {
            BigDecimal calculated = declared.opening().add(movement[0]);
            batch.setBalanceReconciliation(calculated.compareTo(declared.closing()) == 0
                    ? ImportBalanceReconciliation.MATCH : ImportBalanceReconciliation.MISMATCH);
        }
        batch.setBalanceMismatchAcknowledged(false);
        batchRepository.saveAndFlush(batch);
        // Dedupe e categorização antes de encerrar o parse: quem revisa a prévia já vê o que é
        // reenvio e em qual categoria cada linha vai cair.
        deduplicacao.marcarDuplicados(usuarioId, batchId);
        categorizacao.categorizar(usuarioId, batchId);
        // Transição sempre pelo serviço: valida o grafo de estados e emite a métrica.
        batches.transition(usuarioId, batchId, ImportBatchStatus.PARSED, null);
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
