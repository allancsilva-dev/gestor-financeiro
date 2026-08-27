package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.RegraCategoriaRequest;
import com.gestor.financeiro.dto.RegraCategoriaResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.model.enums.TipoCasamentoRegra;
import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.RegraCategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/** Regras determinísticas de categorização do titular (Fase 4). */
@RestController
@RequestMapping("/api/v1/regras-categoria")
@Tag(name = "Categorização", description = "Regras do titular aplicadas antes das heurísticas")
@RequiredArgsConstructor
public class RegraCategoriaController {

    private final RegraCategoriaService service;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @Operation(summary = "Listar regras ativas, na ordem em que decidem")
    public ResponseEntity<List<RegraCategoriaResponse>> listar() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(service.listar(usuarioId).stream().map(RegraCategoriaResponse::de).toList());
    }

    @PostMapping
    @Operation(summary = "Criar regra; mesmo padrão e escopo atualiza o destino em vez de duplicar")
    public ResponseEntity<RegraCategoriaResponse> criar(@Valid @RequestBody RegraCategoriaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        var regra = service.criar(usuarioId, request.padrao(), casamento(request.tipoCasamento()),
                tipo(request.tipoTransacao()), request.categoriaId(), request.prioridade());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegraCategoriaResponse.de(regra));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover regra")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        service.remover(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

    private TipoCasamentoRegra casamento(String valor) {
        if (valor == null || valor.isBlank()) return TipoCasamentoRegra.CONTEM;
        try {
            return TipoCasamentoRegra.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecido) {
            throw new BusinessException("Tipo de casamento inválido");
        }
    }

    private TipoTransacao tipo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return TipoTransacao.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconhecido) {
            throw new BusinessException("Tipo de transação inválido");
        }
    }
}
