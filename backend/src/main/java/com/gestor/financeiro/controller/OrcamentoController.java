package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.OrcamentoRequest;
import com.gestor.financeiro.dto.OrcamentoResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.OrcamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orcamentos")
@Tag(name = "Orçamentos", description = "Orçamento mensal por categoria")
public class OrcamentoController {

    @Autowired
    private OrcamentoService orcamentoService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @GetMapping("/atual")
    @Operation(summary = "Busca ou cria orçamento do mês atual")
    public ResponseEntity<OrcamentoResponse> buscarAtual() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(orcamentoService.buscarOuCriarAtual(usuarioId));
    }

    @GetMapping
    @Operation(summary = "Busca orçamento por mês e ano")
    public ResponseEntity<OrcamentoResponse> buscarPorMes(
            @RequestParam Integer mes,
            @RequestParam Integer ano) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(orcamentoService.buscarPorMes(usuarioId, mes, ano));
    }

    @PostMapping
    @Operation(summary = "Cria ou atualiza orçamento do mês")
    public ResponseEntity<OrcamentoResponse> criarOuAtualizar(@Valid @RequestBody OrcamentoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(orcamentoService.criarOuAtualizar(usuarioId, request));
    }
}
