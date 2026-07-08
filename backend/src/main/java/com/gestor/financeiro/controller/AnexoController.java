package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.AnexoResponse;
import com.gestor.financeiro.model.Anexo;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.AnexoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/anexos")
@Tag(name = "Anexos", description = "Upload e download de comprovantes")
public class AnexoController {

    @Autowired
    private AnexoService anexoService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @PostMapping("/{transacaoId}")
    @Operation(summary = "Enviar anexo para uma transação")
    public ResponseEntity<AnexoResponse> upload(
            @PathVariable Long transacaoId,
            @RequestParam("file") MultipartFile file) throws IOException {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(anexoService.upload(usuarioId, transacaoId, file));
    }

    @GetMapping("/{transacaoId}")
    @Operation(summary = "Listar anexos de uma transação")
    public ResponseEntity<List<AnexoResponse>> listar(@PathVariable Long transacaoId) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(anexoService.listar(usuarioId, transacaoId));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Baixar anexo")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws IOException {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Anexo anexo = anexoService.getAnexo(usuarioId, id);
        byte[] data = anexoService.download(usuarioId, id);

        String contentType = anexo.getTipo() != null ? anexo.getTipo() : "application/octet-stream";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + anexo.getNome() + "\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(data);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir anexo")
    public ResponseEntity<Void> deletar(@PathVariable Long id) throws IOException {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        anexoService.deletar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
