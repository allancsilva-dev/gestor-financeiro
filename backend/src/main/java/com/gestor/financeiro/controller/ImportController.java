package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ImportResultDto;
import com.gestor.financeiro.exception.LegacyImportDisabledException;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/importar")
@Tag(name = "Importação", description = "Importação de dados financeiros")
@RequiredArgsConstructor
public class ImportController {
    private final ImportService importService;
    private final AuthenticatedUserService authenticatedUserService;

    @Value("${app.import.legacy-write-enabled:false}")
    private boolean legacyWriteEnabled;

    @PostMapping("/csv")
    @Operation(summary = "Importar transações via arquivo CSV")
    public ResponseEntity<ImportResultDto> importarCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "carteiraId", required = false) Long carteiraId) {
        if (!legacyWriteEnabled) {
            throw new LegacyImportDisabledException();
        }
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ImportResultDto result = importService.importarCsv(usuarioId, file, carteiraId);
        return ResponseEntity.ok(result);
    }
}
