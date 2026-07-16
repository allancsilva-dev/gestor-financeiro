package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.ReconciliacaoGlobalResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ReconciliacaoGlobalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliacao")
@Tag(name = "Reconciliação", description = "Invariantes financeiros do titular autenticado")
@RequiredArgsConstructor
public class ReconciliacaoGlobalController {
    private final ReconciliacaoGlobalService service;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/global")
    public ResponseEntity<ReconciliacaoGlobalResponse> global() {
        return ResponseEntity.ok(service.reconciliarUsuario(authenticatedUserService.getAuthenticatedUserId()));
    }
}
