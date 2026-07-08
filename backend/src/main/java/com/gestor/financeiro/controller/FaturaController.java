package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.FaturaResponse;
import com.gestor.financeiro.dto.ValorRequest;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.FaturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/faturas")
@Tag(name = "Faturas", description = "Faturas de cartão de crédito")
public class FaturaController {

    @Autowired
    private FaturaService faturaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @GetMapping("/conta/{contaId}/atual")
    @Operation(summary = "Consulta fatura do mês atual (somente leitura)")
    public ResponseEntity<FaturaResponse> buscarAtual(@PathVariable Long contaId) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(faturaService.buscarAtual(usuarioId, contaId));
    }

    @GetMapping("/conta/{contaId}")
    @Operation(summary = "Consulta fatura por mês e ano (somente leitura)")
    public ResponseEntity<FaturaResponse> buscarPorMes(
            @PathVariable Long contaId,
            @RequestParam Integer mes,
            @RequestParam Integer ano) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(faturaService.buscarPorMes(usuarioId, contaId, mes, ano));
    }

    @PostMapping("/conta/{contaId}")
    @Operation(summary = "Cria fatura explicitamente para um mês/ano")
    public ResponseEntity<FaturaResponse> criarFatura(
            @PathVariable Long contaId,
            @RequestParam Integer mes,
            @RequestParam Integer ano) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(faturaService.criarOuBuscarFatura(usuarioId, contaId, mes, ano));
    }

    @PutMapping("/{id}/pagar")
    @Operation(summary = "Pagar fatura")
    public ResponseEntity<FaturaResponse> pagarFatura(
            @PathVariable Long id,
            @Valid @RequestBody ValorRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        return ResponseEntity.ok(faturaService.pagarFatura(usuarioId, id, valor));
    }
}
