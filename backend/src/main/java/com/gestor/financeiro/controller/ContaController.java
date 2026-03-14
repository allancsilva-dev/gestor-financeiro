package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.service.ContaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contas")
public class ContaController {
    
    @Autowired
    private ContaService contaService;
    
    // GET /api/contas/usuario/{usuarioId} - Lista contas do usuário
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Conta>> listar(@PathVariable Long usuarioId) {
        List<Conta> contas = contaService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(contas);
    }
    
    // GET /api/contas/{id} - Busca conta por ID
    @GetMapping("/{id}")
    public ResponseEntity<Conta> buscarPorId(@PathVariable Long id) {
        Conta conta = contaService.buscarPorId(id);
        return ResponseEntity.ok(conta);
    }
    
    // POST /api/contas - Cria nova conta
    @PostMapping
    public ResponseEntity<Conta> criar(@RequestBody Conta conta) {
        Conta contaCriada = contaService.criar(conta);
        return ResponseEntity.ok(contaCriada);
    }
    
    // PUT /api/contas/{id} - Atualiza conta
    @PutMapping("/{id}")
    public ResponseEntity<Conta> atualizar(
        @PathVariable Long id, 
        @RequestBody Conta conta
    ) {
        Conta contaAtualizada = contaService.atualizar(id, conta);
        return ResponseEntity.ok(contaAtualizada);
    }
    
    // DELETE /api/contas/{id} - Deleta conta
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        contaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}