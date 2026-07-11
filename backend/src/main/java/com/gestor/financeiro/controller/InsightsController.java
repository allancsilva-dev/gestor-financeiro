package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.InsightsResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.InsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights", description = "Análises e recomendações financeiras")
@RequiredArgsConstructor
public class InsightsController {
    private final InsightsService insightsService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @Operation(summary = "Gerar insights e recomendações financeiras")
    public ResponseEntity<InsightsResponse> insights() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(insightsService.gerarInsights(usuarioId));
    }
}
