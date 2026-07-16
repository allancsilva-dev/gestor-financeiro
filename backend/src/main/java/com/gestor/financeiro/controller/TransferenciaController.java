package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.TransferenciaRequest;
import com.gestor.financeiro.dto.TransferenciaResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.TransferenciaService;
import com.gestor.financeiro.service.TransferirCommand;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transferencia interna entre contas financeiras do usuario (PR-F2-04).
 * Nunca e receita, despesa ou resultado mensal (ADR-0009).
 */
@RestController
@RequestMapping("/api/v1/transferencias")
@Tag(name = "Transferências", description = "Transferências internas entre contas financeiras")
@RequiredArgsConstructor
public class TransferenciaController {

    private final TransferenciaService transferenciaService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping
    public ResponseEntity<TransferenciaResponse> transferir(@Valid @RequestBody TransferenciaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        TransferenciaService.Resultado resultado = transferenciaService.transferir(new TransferirCommand(
                usuarioId,
                request.getContaOrigemId(),
                request.getContaDestinoId(),
                request.getValor(),
                request.getDescricao(),
                request.getIdempotencyKey(),
                null));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransferenciaResponse.from(resultado));
    }
}
