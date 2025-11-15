package com.gestor.financeiro.controller;

import com.gestor.financeiro.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {
    
    @Autowired
    private DashboardService dashboardService;
    
    // GET /api/dashboard/resumo/{usuarioId} - Retorna resumo completo do dashboard
    @GetMapping("/resumo/{usuarioId}")
    public ResponseEntity<Map<String, Object>> obterResumo(@PathVariable Long usuarioId) {
        Map<String, Object> resumo = dashboardService.obterResumo(usuarioId);
        return ResponseEntity.ok(resumo);
    }
}