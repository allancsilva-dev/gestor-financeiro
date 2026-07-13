package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.service.DashboardService;
import com.gestor.financeiro.service.ProjecaoService;
import com.gestor.financeiro.dto.DashboardDtos;
import com.gestor.financeiro.dto.ProjecaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.model.Usuario;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Indicadores e gráficos financeiros do usuário")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;
    private final ProjecaoService projecaoService;
    private final UsuarioRepository usuarioRepository;

    // GET /api/dashboard/resumo - Resumo geral
    @GetMapping("/resumo")
    @Operation(summary = "Resumo financeiro", description = "Retorna métricas consolidadas do mês")
    public ResponseEntity<DashboardDtos.Resumo> obterResumo(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        DashboardDtos.Resumo resumo = dashboardService.obterResumo(usuario.getId());
        return ResponseEntity.ok(resumo);
    }

    // GET /api/dashboard/gastos-por-categoria - Para gráfico de pizza
    @GetMapping("/gastos-por-categoria")
    @Operation(summary = "Gastos por categoria", description = "Retorna dados para gráfico de pizza")
    public ResponseEntity<List<DashboardDtos.Categoria>> gastosPorCategoria(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        var dados = dashboardService.obterGastosPorCategoria(usuario.getId());
        return ResponseEntity.ok(dados);
    }

    // GET /api/dashboard/evolucao-mensal - Para gráfico de linhas
    @GetMapping("/evolucao-mensal")
    @Operation(summary = "Evolução mensal", description = "Retorna série temporal de entradas e saídas")
    public ResponseEntity<List<DashboardDtos.Evolucao>> evolucaoMensal(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        var dados = dashboardService.obterEvolucaoMensal(usuario.getId());
        return ResponseEntity.ok(dados);
    }

    // GET /api/dashboard/comparacao-mensal - Para gráfico de barras
    @GetMapping("/comparacao-mensal")
    @Operation(summary = "Comparação mensal", description = "Compara mês atual e mês anterior")
    public ResponseEntity<List<DashboardDtos.Comparacao>> comparacaoMensal(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        var dados = dashboardService.obterComparacaoMensal(usuario.getId());
        return ResponseEntity.ok(dados);
    }

    @GetMapping("/projecao")
    @Operation(summary = "Projeção de caixa", description = "Projeta saldo futuro com base em contas fixas e parcelas")
    public ResponseEntity<ProjecaoResponse> projecao(
            Authentication authentication,
            @RequestParam(defaultValue = "6") int meses) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        return ResponseEntity.ok(projecaoService.projetar(usuario.getId(), Math.min(meses, 12)));
    }
}
