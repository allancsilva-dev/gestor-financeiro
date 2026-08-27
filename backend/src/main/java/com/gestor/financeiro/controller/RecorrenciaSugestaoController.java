package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.ContaFixaResponseDto;
import com.gestor.financeiro.dto.RecorrenciaCandidataResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.recorrencia.RecorrenciaCandidataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Padrões de repetição detectados no histórico. São sugestões: viram recorrência só por decisão do
 * titular, e a execução automática continua sendo escolha à parte.
 */
@RestController
@RequestMapping("/api/v1/recorrencias/sugestoes")
@Tag(name = "Recorrências", description = "Padrões detectados no histórico, aguardando decisão")
@RequiredArgsConstructor
public class RecorrenciaSugestaoController {

    private final RecorrenciaCandidataService service;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @Operation(summary = "Listar padrões detectados que ainda aguardam decisão")
    public ResponseEntity<List<RecorrenciaCandidataResponse>> listar() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(service.sugeridas(usuarioId).stream()
                .map(RecorrenciaCandidataResponse::de).toList());
    }

    @PostMapping("/{id}/confirmar")
    @Operation(summary = "Transformar o padrão em recorrência (sem execução automática)")
    public ResponseEntity<ContaFixaResponseDto> confirmar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(service.confirmar(usuarioId, id)));
    }

    @PostMapping("/{id}/descartar")
    @Operation(summary = "Descartar o padrão; ele não volta a ser sugerido")
    public ResponseEntity<Void> descartar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        service.descartar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
