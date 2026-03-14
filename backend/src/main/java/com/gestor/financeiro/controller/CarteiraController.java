package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.service.CarteiraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/carteiras")
public class CarteiraController {
    
    @Autowired
    private CarteiraService carteiraService;
    
    // GET /api/carteiras/usuario/{usuarioId} - Lista carteiras do usuário
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Carteira>> listar(@PathVariable Long usuarioId) {
        List<Carteira> carteiras = carteiraService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(carteiras);
    }
    
    // GET /api/carteiras/{id} - Busca carteira por ID
    @GetMapping("/{id}")
    public ResponseEntity<Carteira> buscarPorId(@PathVariable Long id) {
        Carteira carteira = carteiraService.buscarPorId(id);
        return ResponseEntity.ok(carteira);
    }
    
    // POST /api/carteiras - Cria nova carteira
    @PostMapping
    public ResponseEntity<Carteira> criar(@RequestBody Carteira carteira) {
        Carteira carteiraCriada = carteiraService.criar(carteira);
        return ResponseEntity.ok(carteiraCriada);
    }
    
    // PUT /api/carteiras/{id} - Atualiza carteira
    @PutMapping("/{id}")
    public ResponseEntity<Carteira> atualizar(
        @PathVariable Long id,
        @RequestBody Carteira carteira
    ) {
        Carteira carteiraAtualizada = carteiraService.atualizar(id, carteira);
        return ResponseEntity.ok(carteiraAtualizada);
    }
    
    // POST /api/carteiras/{id}/adicionar - Adiciona dinheiro
    @PostMapping("/{id}/adicionar")
    public ResponseEntity<Carteira> adicionarDinheiro(
        @PathVariable Long id,
        @RequestBody Map<String, BigDecimal> request
    ) {
        BigDecimal valor = request.get("valor");
        Carteira carteira = carteiraService.adicionarDinheiro(id, valor);
        return ResponseEntity.ok(carteira);
    }
    
    // POST /api/carteiras/{id}/remover - Remove dinheiro
    @PostMapping("/{id}/remover")
    public ResponseEntity<Carteira> removerDinheiro(
        @PathVariable Long id,
        @RequestBody Map<String, BigDecimal> request
    ) {
        BigDecimal valor = request.get("valor");
        Carteira carteira = carteiraService.removerDinheiro(id, valor);
        return ResponseEntity.ok(carteira);
    }
    
    // GET /api/carteiras/usuario/{usuarioId}/saldo-total - Calcula saldo total
    @GetMapping("/usuario/{usuarioId}/saldo-total")
    public ResponseEntity<BigDecimal> calcularSaldoTotal(@PathVariable Long usuarioId) {
        BigDecimal saldoTotal = carteiraService.calcularSaldoTotal(usuarioId);
        return ResponseEntity.ok(saldoTotal);
    }
    
    // DELETE /api/carteiras/{id} - Deleta carteira
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        carteiraService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}