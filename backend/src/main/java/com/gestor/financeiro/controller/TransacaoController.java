package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.TransacaoRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.TransacaoService;
import jakarta.validation.Valid;
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

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/transacoes/minhas - Lista transações do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<List<Transacao>> listar() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        List<Transacao> transacoes = transacaoService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(transacoes);
    }
    
    // GET /api/transacoes/periodo - Lista transações por período
    @GetMapping("/periodo")
    public ResponseEntity<List<Transacao>> listarPorPeriodo(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        List<Transacao> transacoes = transacaoService.listarPorPeriodo(usuarioId, inicio, fim);
        return ResponseEntity.ok(transacoes);
    }
    
    // GET /api/transacoes/{id} - Busca transação por ID
    @GetMapping("/{id}")
    public ResponseEntity<Transacao> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = transacaoService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(transacao);
    }
    
    // POST /api/transacoes - Cria nova transação
    @PostMapping
    public ResponseEntity<Transacao> criar(@Valid @RequestBody TransacaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = toEntity(request);
        Transacao transacaoCriada = transacaoService.criar(transacao, usuarioId);
        return ResponseEntity.ok(transacaoCriada);
    }
    
    // PUT /api/transacoes/{id} - Atualiza transação
    @PutMapping("/{id}")
    public ResponseEntity<Transacao> atualizar(
        @PathVariable Long id, 
        @Valid @RequestBody TransacaoRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = toEntity(request);
        Transacao transacaoAtualizada = transacaoService.atualizar(id, transacao, usuarioId);
        return ResponseEntity.ok(transacaoAtualizada);
    }
    
    // DELETE /api/transacoes/{id} - Deleta transação
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        transacaoService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    private Transacao toEntity(TransacaoRequest request) {
        Transacao transacao = new Transacao();
        transacao.setDescricao(request.getDescricao());
        transacao.setValorTotal(request.getValor());
        transacao.setData(request.getData());
        transacao.setTipo(request.getTipo());
        transacao.setObservacoes(request.getObservacoes());
        transacao.setParcelado(request.getParcelado() != null ? request.getParcelado() : false);
        transacao.setTotalParcelas(request.getTotalParcelas());
        transacao.setRecorrente(request.getRecorrente() != null ? request.getRecorrente() : false);

        Categoria categoria = new Categoria();
        categoria.setId(request.getCategoriaIdNormalizada());
        transacao.setCategoria(categoria);

        if (request.getContaIdNormalizada() != null) {
            Conta conta = new Conta();
            conta.setId(request.getContaIdNormalizada());
            transacao.setConta(conta);
        }

        return transacao;
    }
}