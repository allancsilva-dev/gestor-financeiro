package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.security.AuthenticatedUserService;
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

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/contas/minhas - Lista contas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<List<Conta>> listar() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        List<Conta> contas = contaService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(contas);
    }
    
    // GET /api/contas/{id} - Busca conta por ID
    @GetMapping("/{id}")
    public ResponseEntity<Conta> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Conta conta = contaService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(conta);
    }
    
    // POST /api/contas - Cria nova conta
    @PostMapping
    public ResponseEntity<Conta> criar(@RequestBody Conta conta) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Conta contaCriada = contaService.criar(conta, usuarioId);
        return ResponseEntity.ok(contaCriada);
    }
    
    // PUT /api/contas/{id} - Atualiza conta
    @PutMapping("/{id}")
    public ResponseEntity<Conta> atualizar(
        @PathVariable Long id, 
        @RequestBody Conta conta
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Conta contaAtualizada = contaService.atualizar(id, conta, usuarioId);
        return ResponseEntity.ok(contaAtualizada);
    }
    
    // DELETE /api/contas/{id} - Deleta conta
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        contaService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}