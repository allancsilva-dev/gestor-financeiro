package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.ImportResultDto;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/importar")
@Tag(name = "Importação", description = "Importação de dados financeiros")
public class ImportController {

    @Autowired
    private ImportService importService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @PostMapping("/csv")
    @Operation(summary = "Importar transações via arquivo CSV")
    public ResponseEntity<ImportResultDto> importarCsv(@RequestParam("file") MultipartFile file) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ImportResultDto result = importService.importarCsv(usuarioId, file);
        return ResponseEntity.ok(result);
    }
}
