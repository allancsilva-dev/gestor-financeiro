package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/exportar")
@Tag(name = "Exportação", description = "Exportação de dados financeiros")
@RequiredArgsConstructor
public class ExportController {
    private final ExportService exportService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/transacoes")
    @Operation(summary = "Exportar transações em CSV")
    public ResponseEntity<String> exportarTransacoes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        String csv = exportService.exportarTransacoesCsv(usuarioId, inicio, fim);
        return csvResponse(csv, "transacoes.csv");
    }

    @GetMapping("/categorias")
    @Operation(summary = "Exportar categorias em CSV")
    public ResponseEntity<String> exportarCategorias() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        String csv = exportService.exportarCategoriasCsv(usuarioId);
        return csvResponse(csv, "categorias.csv");
    }

    @GetMapping("/contas")
    @Operation(summary = "Exportar contas em CSV")
    public ResponseEntity<String> exportarContas() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        String csv = exportService.exportarContasCsv(usuarioId);
        return csvResponse(csv, "contas.csv");
    }

    @GetMapping("/completo")
    @Operation(summary = "Exportar todos os dados em CSV")
    public ResponseEntity<String> exportarCompleto() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        String csv = exportService.exportarCompletoCsv(usuarioId);
        return csvResponse(csv, "dados-completos.csv");
    }

    private ResponseEntity<String> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
