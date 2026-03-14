package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.MetaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metas")
public class MetaController {
    
    @Autowired
    private MetaService metaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/metas/minhas - Lista metas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<List<Meta>> listar() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        List<Meta> metas = metaService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(metas);
    }
    
    // GET /api/metas/{id} - Busca meta por ID
    @GetMapping("/{id}")
    public ResponseEntity<Meta> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta meta = metaService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(meta);
    }
    
    // GET /api/metas/{id}/progresso - Calcula progresso da meta
    @GetMapping("/{id}/progresso")
    public ResponseEntity<Map<String, Object>> calcularProgresso(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta meta = metaService.buscarPorIdDoUsuario(id, usuarioId);
        BigDecimal progresso = metaService.calcularProgresso(id, usuarioId);
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("metaId", id);
        resultado.put("valorTotal", meta.getValorTotal());
        resultado.put("valorReservado", meta.getValorReservado());
        resultado.put("valorRestante", meta.getValorTotal().subtract(meta.getValorReservado()));
        resultado.put("progresso", progresso); // Porcentagem
        
        return ResponseEntity.ok(resultado);
    }
    
    // POST /api/metas - Cria nova meta
    @PostMapping
    public ResponseEntity<Meta> criar(@RequestBody Meta meta) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta metaCriada = metaService.criar(meta, usuarioId);
        return ResponseEntity.ok(metaCriada);
    }
    
    // PUT /api/metas/{id} - Atualiza meta
    @PutMapping("/{id}")
    public ResponseEntity<Meta> atualizar(
        @PathVariable Long id, 
        @RequestBody Meta meta
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta metaAtualizada = metaService.atualizar(id, meta, usuarioId);
        return ResponseEntity.ok(metaAtualizada);
    }
    
    // PUT /api/metas/{id}/adicionar - Adiciona valor à meta
    @PutMapping("/{id}/adicionar")
    public ResponseEntity<Meta> adicionarValor(
        @PathVariable Long id, 
        @RequestBody Map<String, BigDecimal> request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.get("valor");
        Meta meta = metaService.adicionarValor(id, valor, usuarioId);
        return ResponseEntity.ok(meta);
    }
    
    // PUT /api/metas/{id}/remover - Remove valor da meta
    @PutMapping("/{id}/remover")
    public ResponseEntity<Meta> removerValor(
        @PathVariable Long id, 
        @RequestBody Map<String, BigDecimal> request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.get("valor");
        Meta meta = metaService.removerValor(id, valor, usuarioId);
        return ResponseEntity.ok(meta);
    }
    
    // DELETE /api/metas/{id} - Deleta meta
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        metaService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}