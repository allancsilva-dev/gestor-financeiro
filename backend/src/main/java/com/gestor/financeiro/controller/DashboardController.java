package com.gestor.financeiro.controller;

import com.gestor.financeiro.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.model.Usuario;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // GET /api/dashboard/resumo - Resumo geral
    @GetMapping("/resumo")
    public ResponseEntity<Map<String, Object>> obterResumo(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        Map<String, Object> resumo = dashboardService.obterResumo(usuario.getId());
        return ResponseEntity.ok(resumo);
    }

    // GET /api/dashboard/gastos-por-categoria - Para gráfico de pizza
    @GetMapping("/gastos-por-categoria")
    public ResponseEntity<?> gastosPorCategoria(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        var dados = dashboardService.obterGastosPorCategoria(usuario.getId());
        return ResponseEntity.ok(dados);
    }

    // GET /api/dashboard/evolucao-mensal - Para gráfico de linhas
    @GetMapping("/evolucao-mensal")
    public ResponseEntity<?> evolucaoMensal(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        var dados = dashboardService.obterEvolucaoMensal(usuario.getId());
        return ResponseEntity.ok(dados);
    }

    // GET /api/dashboard/comparacao-mensal - Para gráfico de barras
    @GetMapping("/comparacao-mensal")
    public ResponseEntity<?> comparacaoMensal(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        var dados = dashboardService.obterComparacaoMensal(usuario.getId());
        return ResponseEntity.ok(dados);
    }
}