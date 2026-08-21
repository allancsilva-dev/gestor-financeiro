package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.config.IdempotencyFilter;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.InvestimentoService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/investimentos")
@Tag(name = "Investimentos", description = "Gerenciamento de ativos e movimentacoes")
@RequiredArgsConstructor
public class InvestimentoController {
    private final InvestimentoService investimentoService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping
    @Operation(summary = "Criar ativo")
    public ResponseEntity<AtivoResponse> criar(@RequestBody AtivoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(investimentoService.criarAtivo(usuarioId, request));
    }

    @GetMapping
    @Operation(summary = "Listar ativos")
    public ResponseEntity<Page<AtivoResponse>> listar(
            @PageableDefault(size = 20, sort = "ticker", direction = Sort.Direction.ASC) Pageable pageable) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        return ResponseEntity.ok(investimentoService.listarAtivos(usuarioId, cappedPageable));
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
            @PathVariable Long ativoId,
            @RequestHeader(value = IdempotencyFilter.HEADER, required = false) String idempotencyKey,
            @RequestBody MovimentacaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(
                investimentoService.adicionarMovimentacao(usuarioId, ativoId, request, idempotencyKey));
    }

    @GetMapping("/{ativoId}/movimentacoes")
    @Operation(summary = "Listar movimentacoes do ativo")
    public ResponseEntity<Page<MovimentacaoResponse>> listarMovimentacoes(
            @PathVariable Long ativoId,
            @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC) Pageable pageable) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        return ResponseEntity.ok(investimentoService.listarMovimentacoes(usuarioId, ativoId, cappedPageable));
    }
}
