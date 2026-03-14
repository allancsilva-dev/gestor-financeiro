package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.service.TransacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transacoes")
public class TransacaoController {
    
    @Autowired
    private TransacaoService transacaoService;
    
    // GET /api/transacoes/usuario/{usuarioId} - Lista transações do usuário
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Transacao>> listar(@PathVariable Long usuarioId) {
        List<Transacao> transacoes = transacaoService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(transacoes);
    }
    
    // GET /api/transacoes/periodo - Lista transações por período
    @GetMapping("/periodo")
    public ResponseEntity<List<Transacao>> listarPorPeriodo(
        @RequestParam Long usuarioId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        List<Transacao> transacoes = transacaoService.listarPorPeriodo(usuarioId, inicio, fim);
        return ResponseEntity.ok(transacoes);
    }
    
    // GET /api/transacoes/{id} - Busca transação por ID
    @GetMapping("/{id}")
    public ResponseEntity<Transacao> buscarPorId(@PathVariable Long id) {
        Transacao transacao = transacaoService.buscarPorId(id);
        return ResponseEntity.ok(transacao);
    }
    
    // POST /api/transacoes - Cria nova transação
    @PostMapping
    public ResponseEntity<Transacao> criar(@RequestBody Transacao transacao) {
        Transacao transacaoCriada = transacaoService.criar(transacao);
        return ResponseEntity.ok(transacaoCriada);
    }
    
    // PUT /api/transacoes/{id} - Atualiza transação
    @PutMapping("/{id}")
    public ResponseEntity<Transacao> atualizar(
        @PathVariable Long id, 
        @RequestBody Transacao transacao
    ) {
        Transacao transacaoAtualizada = transacaoService.atualizar(id, transacao);
        return ResponseEntity.ok(transacaoAtualizada);
    }
    
    // DELETE /api/transacoes/{id} - Deleta transação
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        transacaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}