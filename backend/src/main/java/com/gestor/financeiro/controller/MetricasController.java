package com.gestor.financeiro.controller;

import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.MetricasService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Metricas oficiais do produto (ADR-0013, PR-F2-15/16) com drill-down ate a
 * origem de cada numero.
 */
@RestController
@RequestMapping("/api/v1/metricas")
@Tag(name = "Métricas", description = "As 9 métricas oficiais (ADR-0013) e suas origens")
@RequiredArgsConstructor
public class MetricasController {

    private final MetricasService metricasService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ResponseEntity<MetricasService.Metricas> obter() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(metricasService.calcular(usuarioId));
    }

    @GetMapping("/{metrica}/origens")
    public ResponseEntity<List<MetricasService.Origem>> origens(@PathVariable String metrica) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(metricasService.origens(usuarioId, metrica));
    }
}
