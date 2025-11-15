package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.service.ContaFixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contas-fixas")
@CrossOrigin(origins = "*")
public class ContaFixaController {
    
    @Autowired
    private ContaFixaService contaFixaService;
    
    // GET /api/contas-fixas/usuario/{usuarioId} - Lista contas fixas do usuário
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<ContaFixa>> listar(@PathVariable Long usuarioId) {
        List<ContaFixa> contasFixas = contaFixaService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(contasFixas);
    }
    
    // GET /api/contas-fixas/{id} - Busca conta fixa por ID
    @GetMapping("/{id}")
    public ResponseEntity<ContaFixa> buscarPorId(@PathVariable Long id) {
        ContaFixa contaFixa = contaFixaService.buscarPorId(id);
        return ResponseEntity.ok(contaFixa);
    }
    
    // POST /api/contas-fixas - Cria nova conta fixa
    @PostMapping
    public ResponseEntity<ContaFixa> criar(@RequestBody ContaFixa contaFixa) {
        ContaFixa contaFixaCriada = contaFixaService.criar(contaFixa);
        return ResponseEntity.ok(contaFixaCriada);
    }
    
    // PUT /api/contas-fixas/{id} - Atualiza conta fixa
    @PutMapping("/{id}")
    public ResponseEntity<ContaFixa> atualizar(
        @PathVariable Long id, 
        @RequestBody ContaFixa contaFixa
    ) {
        ContaFixa contaFixaAtualizada = contaFixaService.atualizar(id, contaFixa);
        return ResponseEntity.ok(contaFixaAtualizada);
    }
    
    // PUT /api/contas-fixas/{id}/pagar - Marca conta como paga
    @PutMapping("/{id}/pagar")
    public ResponseEntity<ContaFixa> marcarComoPaga(
        @PathVariable Long id,
        @RequestBody Map<String, BigDecimal> request
    ) {
        BigDecimal valorPago = request.get("valorPago");
        ContaFixa contaFixa = contaFixaService.marcarComoPaga(id, valorPago);
        return ResponseEntity.ok(contaFixa);
    }
    
    // DELETE /api/contas-fixas/{id} - Deleta conta fixa
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        contaFixaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}