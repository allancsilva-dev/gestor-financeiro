package com.gestor.financeiro.controller;

import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CompromissosService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Compromissos proximos (PR-F3-01): total oficial Comprometido + itens
 * FATURA/PARCELA (COMPROMETIDO) e contas fixas (PREVISTO, fora do total).
 */
@RestController
@RequestMapping("/api/v1/compromissos")
@Tag(name = "Compromissos", description = "Compromissos próximos: Comprometido oficial + contas fixas previstas")
@RequiredArgsConstructor
public class CompromissosController {

    private final CompromissosService compromissosService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ResponseEntity<CompromissosService.Compromissos> listar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(compromissosService.listar(usuarioId, ate));
    }
}
