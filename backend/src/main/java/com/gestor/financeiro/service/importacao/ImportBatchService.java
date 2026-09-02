package com.gestor.financeiro.service.importacao;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.FinancialConflictException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFormat;
import com.gestor.financeiro.model.enums.ImportOrigin;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.repository.ImportBatchRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ImportBatchService {
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final Map<ImportBatchStatus, EnumSet<ImportBatchStatus>> TRANSITIONS = transitions();

    private final ImportBatchRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final ImportObservability observability;
    private final InstituicaoResolver instituicoes;

    public ImportBatchService(ImportBatchRepository repository, UsuarioRepository usuarioRepository,
                              ImportObservability observability, InstituicaoResolver instituicoes) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.observability = observability;
        this.instituicoes = instituicoes;
    }

    /**
     * Cria o lote com a proveniência explícita.
     *
     * <p>{@code origem} é parâmetro, e não default silencioso, porque o CHECK
     * {@code ck_import_batches_origin_formato} recusa lote de conector rotulado como envio manual —
     * e um default que ninguém enxerga é exatamente como esse tipo de rótulo fica errado.</p>
     */
    @Transactional
    public ImportBatch create(Long usuarioId, ImportFormat format, String institutionCode,
                              String fileSha256, String idempotencyKey, ImportOrigin origem) {
        validate(format, fileSha256, idempotencyKey);
        // Serializa submissões do mesmo titular; unique parcial continua como backstop.
        Usuario usuario = usuarioRepository.findByIdComLock(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        if (idempotencyKey != null) {
            var existing = repository.findByUsuarioIdAndIdempotencyKey(usuarioId, idempotencyKey);
            if (existing.isPresent()) {
                ImportBatch batch = existing.get();
                // Identidade do reenvio é o conteúdo: o formato do lote existente já foi detectado,
                // enquanto o reenvio chega como UNKNOWN. Exigir igualdade de formato transformaria
                // replay legítimo em 409.
                boolean mesmoConteudo = batch.getFileSha256().equals(fileSha256);
                boolean formatoCompativel = format == ImportFormat.UNKNOWN || batch.getFormat() == format;
                if (mesmoConteudo && formatoCompativel) return batch;
                throw new FinancialConflictException("Idempotency-Key já usada para outra importação");
            }
        }
        ImportBatch batch = new ImportBatch();
        batch.setUsuario(usuario);
        batch.setFormat(format);
        batch.setOrigin(origem == null ? ImportOrigin.UPLOAD : origem);
        batch.setInstitutionCode(normalizeInstitution(institutionCode));
        batch.setFileSha256(fileSha256);
        batch.setIdempotencyKey(idempotencyKey);
        ImportBatch saved = repository.save(batch);
        observability.transition(saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public ImportBatch get(Long usuarioId, Long batchId) {
        return repository.findByIdAndUsuarioId(batchId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Importação não encontrada"));
    }

    @Transactional
    public ImportBatch transition(Long usuarioId, Long batchId, ImportBatchStatus target,
                                  ImportFailureCode failureCode) {
        ImportBatch batch = get(usuarioId, batchId);
        if (target == null || !TRANSITIONS.getOrDefault(batch.getStatus(), EnumSet.noneOf(ImportBatchStatus.class))
                .contains(target)) {
            throw new FinancialConflictException("Transição de importação inválida: " + batch.getStatus()
                    + " -> " + target);
        }
        if (target == ImportBatchStatus.FAILED) {
            if (failureCode == null) throw new BusinessException("Código de falha é obrigatório");
            batch.setFailureCode(failureCode.name());
            observability.failure(failureCode);
        } else if (failureCode != null) {
            throw new BusinessException("Código de falha só é permitido no estado FAILED");
        }
        batch.setStatus(target);
        ImportBatch saved = repository.save(batch);
        observability.transition(target);
        return saved;
    }

    @Transactional
    public ImportBatch setDetected(Long usuarioId, Long batchId, ImportFormat format, String institutionCode) {
        if (format == null || format == ImportFormat.UNKNOWN) throw new BusinessException("Formato detectado inválido");
        ImportBatch batch = get(usuarioId, batchId);
        if (batch.getStatus() != ImportBatchStatus.RECEIVED || batch.getFormat() != ImportFormat.UNKNOWN)
            throw new FinancialConflictException("Detecção só é permitida para importação recebida");
        batch.setFormat(format);
        String normalizado = normalizeInstitution(institutionCode);
        batch.setInstitutionCode(normalizado);
        // Código detectado é texto da fonte; a instituição canônica é o que faz OFX e conector
        // convergirem na deduplicação. Catálogo sem a instituição deixa nulo e cai no texto.
        batch.setInstituicao(instituicoes.resolver(normalizado).orElse(null));
        return repository.save(batch);
    }

    private void validate(ImportFormat format, String fileSha256, String idempotencyKey) {
        if (format == null) throw new BusinessException("Formato é obrigatório");
        if (fileSha256 == null || !SHA256.matcher(fileSha256).matches()) {
            throw new BusinessException("SHA-256 do arquivo inválido");
        }
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessException("Idempotency-Key inválida");
        }
    }

    private String normalizeInstitution(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9._-]{1,80}")) {
            throw new BusinessException("Código da instituição inválido");
        }
        return normalized;
    }

    private static Map<ImportBatchStatus, EnumSet<ImportBatchStatus>> transitions() {
        Map<ImportBatchStatus, EnumSet<ImportBatchStatus>> map = new EnumMap<>(ImportBatchStatus.class);
        map.put(ImportBatchStatus.RECEIVED, EnumSet.of(ImportBatchStatus.PARSED, ImportBatchStatus.FAILED));
        map.put(ImportBatchStatus.PARSED, EnumSet.of(ImportBatchStatus.PENDING_REVIEW,
                ImportBatchStatus.READY_TO_COMMIT, ImportBatchStatus.FAILED));
        map.put(ImportBatchStatus.PENDING_REVIEW, EnumSet.of(ImportBatchStatus.READY_TO_COMMIT,
                ImportBatchStatus.FAILED));
        map.put(ImportBatchStatus.READY_TO_COMMIT, EnumSet.of(ImportBatchStatus.COMMITTING,
                ImportBatchStatus.FAILED));
        map.put(ImportBatchStatus.COMMITTING, EnumSet.of(ImportBatchStatus.COMMITTED,
                ImportBatchStatus.FAILED));
        map.put(ImportBatchStatus.COMMITTED, EnumSet.of(ImportBatchStatus.REVERSED));
        return Map.copyOf(map);
    }
}
