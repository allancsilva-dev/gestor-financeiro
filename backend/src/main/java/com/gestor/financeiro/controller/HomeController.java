package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.HomeResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payload unico da home (V42): mantem a tela em dois requests. */
@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Home", description = "Agregado da tela inicial")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @Operation(summary = "Métricas, compromissos, totais do mês, categorias e não lidas")
    public ResponseEntity<HomeResponse> obter() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(homeService.montar(usuarioId));
    }
}
