package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.InvestimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/investimentos")
@Tag(name = "Investimentos", description = "Gerenciamento de ativos e movimentacoes")
public class InvestimentoController {

    @Autowired
    private InvestimentoService investimentoService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @PostMapping
    @Operation(summary = "Criar ativo")
    public ResponseEntity<AtivoResponse> criar(@RequestBody AtivoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(investimentoService.criarAtivo(usuarioId, request));
    }

    @GetMapping
    @Operation(summary = "Listar ativos")
    public ResponseEntity<List<AtivoResponse>> listar() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(investimentoService.listarAtivos(usuarioId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar ativo")
    public ResponseEntity<AtivoResponse> atualizar(@PathVariable Long id, @RequestBody AtivoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(investimentoService.atualizarAtivo(usuarioId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir ativo")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        investimentoService.deletarAtivo(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ativoId}/movimentacoes")
    @Operation(summary = "Adicionar movimentacao")
    public ResponseEntity<MovimentacaoResponse> adicionarMovimentacao(
            @PathVariable Long ativoId, @RequestBody MovimentacaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(investimentoService.adicionarMovimentacao(usuarioId, ativoId, request));
    }

    @GetMapping("/{ativoId}/movimentacoes")
    @Operation(summary = "Listar movimentacoes do ativo")
    public ResponseEntity<List<MovimentacaoResponse>> listarMovimentacoes(@PathVariable Long ativoId) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(investimentoService.listarMovimentacoes(usuarioId, ativoId));
    }
}
