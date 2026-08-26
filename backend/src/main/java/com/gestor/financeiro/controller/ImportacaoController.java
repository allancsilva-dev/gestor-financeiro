package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.IdempotencyFilter;
import com.gestor.financeiro.dto.ImportBatchResponse;
import com.gestor.financeiro.dto.ImportRecordPageResponse;
import com.gestor.financeiro.dto.ImportRecordResponse;
import com.gestor.financeiro.model.ImportBatch;
import com.gestor.financeiro.model.enums.ImportBatchStatus;
import com.gestor.financeiro.model.enums.ImportFailureCode;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.importacao.CanonicalImportOrchestrator;
import com.gestor.financeiro.service.importacao.ImportAdmissionService;
import com.gestor.financeiro.service.importacao.ImportBatchService;
import com.gestor.financeiro.service.importacao.ImportPreviewService;
import com.gestor.financeiro.service.importacao.ImportParsingException;
import com.gestor.financeiro.service.importacao.TempFileImportSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Pipeline canônico de importação. O caminho legado (`/api/v1/importar/csv`) grava direto no
 * ledger e continua desligado por padrão; aqui o arquivo vira lote auditável antes de qualquer
 * escrita financeira.
 */
@RestController
@RequestMapping("/api/v1/importacoes")
@Tag(name = "Importação", description = "Pipeline canônico de importação CSV/OFX")
@RequiredArgsConstructor
public class ImportacaoController {

    private final CanonicalImportOrchestrator orchestrator;
    private final ImportBatchService batches;
    private final ImportAdmissionService admission;
    private final ImportPreviewService preview;
    private final AuthenticatedUserService authenticatedUserService;

    @Value("${spring.servlet.multipart.location:${java.io.tmpdir}}")
    private String diretorioTemporario;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enviar arquivo CSV/OFX para revisão antes do lançamento")
    public ResponseEntity<ImportBatchResponse> enviar(@RequestParam("file") MultipartFile file,
                                                      HttpServletRequest request) throws IOException {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        String idempotencyKey = (String) request.getAttribute(IdempotencyFilter.ATTRIBUTE);

        // A vaga de parse e o arquivo temporário são liberados em qualquer saída, inclusive erro.
        try (ImportAdmissionService.Passe passe = admission.admitir(usuarioId);
             TempFileImportSource source = TempFileImportSource.of(file, Path.of(diretorioTemporario))) {
            ImportBatch batch = orchestrator.stage(usuarioId, source, idempotencyKey);
            // Replay de uma chave cujo lote falhou volta pelo mesmo caminho de erro da primeira
            // tentativa: mesma requisição, mesma resposta.
            if (batch.getStatus() == ImportBatchStatus.FAILED) {
                throw new ImportParsingException(ImportFailureCode.valueOf(batch.getFailureCode()),
                        "Importação anterior com a mesma chave falhou");
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(ImportBatchResponse.de(batch));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar situação de uma importação")
    public ResponseEntity<ImportBatchResponse> consultar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ImportBatchResponse.de(batches.get(usuarioId, id)));
    }

    @GetMapping("/{id}/registros")
    @Operation(summary = "Revisar as linhas normalizadas do arquivo antes de confirmar")
    public ResponseEntity<ImportRecordPageResponse> registros(
            @PathVariable Long id,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "aposLinha", defaultValue = "0") int aposLinha,
            @RequestParam(value = "tamanho", defaultValue = "50") int tamanho) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(preview.pagina(usuarioId, id, status, aposLinha, tamanho));
    }
}
