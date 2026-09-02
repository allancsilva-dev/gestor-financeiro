package com.gestor.financeiro;

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
import com.gestor.financeiro.service.importacao.ImportBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportBatchServiceTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired ImportBatchService service;
    @Autowired ImportBatchRepository repository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void setup() {
        usuario = usuarioRepository.save(TestDataFactory.usuario("Import", "import@test.local", "hash"));
    }

    @Test
    void createNormalizesInstitutionAndReplaysSameIdempotencyKey() {
        ImportBatch first = service.create(usuario.getId(), ImportFormat.CSV, " nubank ", HASH_A, "import:1", ImportOrigin.UPLOAD);
        ImportBatch replay = service.create(usuario.getId(), ImportFormat.CSV, "NUBANK", HASH_A, "import:1", ImportOrigin.UPLOAD);

        assertEquals(first.getId(), replay.getId());
        assertEquals("NUBANK", first.getInstitutionCode());
        assertEquals(ImportBatchStatus.RECEIVED, first.getStatus());
        assertEquals(1, repository.findAll().stream().filter(b -> b.getUsuario().getId().equals(usuario.getId())).count());
    }

    @Test
    void replayAposDeteccaoDevolveOMesmoLote() {
        ImportBatch first = service.create(usuario.getId(), ImportFormat.UNKNOWN, null, HASH_A, "import:replay", ImportOrigin.UPLOAD);
        service.setDetected(usuario.getId(), first.getId(), ImportFormat.CSV, "NUBANK");

        ImportBatch replay = service.create(usuario.getId(), ImportFormat.UNKNOWN, null, HASH_A, "import:replay", ImportOrigin.UPLOAD);

        assertEquals(first.getId(), replay.getId());
        assertEquals(ImportFormat.CSV, replay.getFormat());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentContentConflicts() {
        service.create(usuario.getId(), ImportFormat.CSV, null, HASH_A, "import:2", ImportOrigin.UPLOAD);
        assertThrows(FinancialConflictException.class,
                () -> service.create(usuario.getId(), ImportFormat.CSV, null, HASH_B, "import:2", ImportOrigin.UPLOAD));
    }

    @Test
    void lifecycleAllowsOnlyDeclaredTransitions() {
        ImportBatch batch = service.create(usuario.getId(), ImportFormat.OFX, "001", HASH_A, null, ImportOrigin.UPLOAD);
        service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.PARSED, null);
        service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.PENDING_REVIEW, null);
        service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.READY_TO_COMMIT, null);
        service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.COMMITTING, null);
        service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.COMMITTED, null);
        ImportBatch reversed = service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.REVERSED, null);

        assertEquals(ImportBatchStatus.REVERSED, reversed.getStatus());
        assertThrows(FinancialConflictException.class,
                () -> service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.PARSED, null));
    }

    @Test
    void failureRequiresBoundedCode() {
        ImportBatch batch = service.create(usuario.getId(), ImportFormat.CSV, null, HASH_A, null, ImportOrigin.UPLOAD);
        assertThrows(BusinessException.class,
                () -> service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.FAILED, null));
        ImportBatch failed = service.transition(usuario.getId(), batch.getId(), ImportBatchStatus.FAILED,
                ImportFailureCode.FILE_LIMIT_EXCEEDED);
        assertEquals("FILE_LIMIT_EXCEEDED", failed.getFailureCode());
    }

    @Test
    void ownershipCrossTenantLooksNotFound() {
        Usuario other = usuarioRepository.save(TestDataFactory.usuario("Other", "other-import@test.local", "hash"));
        ImportBatch batch = service.create(usuario.getId(), ImportFormat.CSV, null, HASH_A, null, ImportOrigin.UPLOAD);
        assertThrows(ResourceNotFoundException.class, () -> service.get(other.getId(), batch.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.transition(other.getId(), batch.getId(), ImportBatchStatus.PARSED, null));
    }

    @Test
    void rejectsMalformedBoundaryValues() {
        assertThrows(BusinessException.class,
                () -> service.create(usuario.getId(), ImportFormat.CSV, null, "ABC", null, ImportOrigin.UPLOAD));
        assertThrows(BusinessException.class,
                () -> service.create(usuario.getId(), ImportFormat.CSV, "../../bank", HASH_A, null, ImportOrigin.UPLOAD));
        assertThrows(BusinessException.class,
                () -> service.create(usuario.getId(), ImportFormat.CSV, null, HASH_A, "contains space", ImportOrigin.UPLOAD));
    }
}
