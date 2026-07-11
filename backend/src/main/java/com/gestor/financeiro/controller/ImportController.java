package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ImportResultDto;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/importar")
@Tag(name = "Importação", description = "Importação de dados financeiros")
@RequiredArgsConstructor
public class ImportController {
    private final ImportService importService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/csv")
    @Operation(summary = "Importar transações via arquivo CSV")
    public ResponseEntity<ImportResultDto> importarCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "carteiraId", required = false) Long carteiraId) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ImportResultDto result = importService.importarCsv(usuarioId, file, carteiraId);
        return ResponseEntity.ok(result);
    }
}
