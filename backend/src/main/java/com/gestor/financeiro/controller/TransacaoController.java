package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.TransacaoRequest;
import com.gestor.financeiro.dto.TransacaoResponseDto;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.TransacaoService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transacoes")
@Tag(name = "Transações", description = "Gestão de receitas e despesas do usuário autenticado")
public class TransacaoController {
    
    @Autowired
    private TransacaoService transacaoService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/transacoes/minhas - Lista transações do usuário autenticado
    @GetMapping("/minhas")
    @Operation(summary = "Listar transações", description = "Retorna transações paginadas do usuário autenticado")
    public ResponseEntity<Page<TransacaoResponseDto>> listar(
        @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Transacao> transacoes = transacaoService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(transacoes.map(TransacaoResponseDto::fromEntity));
    }
    
    // GET /api/transacoes/periodo - Lista transações por período
    @GetMapping("/periodo")
    @Operation(summary = "Listar por período", description = "Retorna transações paginadas filtradas por intervalo de datas")
    public ResponseEntity<Page<TransacaoResponseDto>> listarPorPeriodo(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
        @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Transacao> transacoes = transacaoService.listarPorPeriodo(usuarioId, inicio, fim, cappedPageable);
        return ResponseEntity.ok(transacoes.map(TransacaoResponseDto::fromEntity));
    }
    
    // GET /api/transacoes/{id} - Busca transação por ID
    @GetMapping("/{id}")
    @Operation(summary = "Buscar transação por ID", description = "Retorna uma transação específica validando ownership")
    public ResponseEntity<TransacaoResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = transacaoService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(TransacaoResponseDto.fromEntity(transacao));
    }
    
    // POST /api/transacoes - Cria nova transação
    @PostMapping
    @Operation(summary = "Criar transação", description = "Cria uma nova transação para o usuário autenticado")
    public ResponseEntity<TransacaoResponseDto> criar(@Valid @RequestBody TransacaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = toEntity(request);
        Transacao transacaoCriada = transacaoService.criar(transacao, usuarioId);
        return ResponseEntity.ok(TransacaoResponseDto.fromEntity(transacaoCriada));
    }
    
    // PUT /api/transacoes/{id} - Atualiza transação
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação", description = "Atualiza uma transação existente validando ownership")
    public ResponseEntity<TransacaoResponseDto> atualizar(
        @PathVariable Long id, 
        @Valid @RequestBody TransacaoRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Transacao transacao = toEntity(request);
        Transacao transacaoAtualizada = transacaoService.atualizar(id, transacao, usuarioId);
        return ResponseEntity.ok(TransacaoResponseDto.fromEntity(transacaoAtualizada));
    }
    
    // DELETE /api/transacoes/{id} - Deleta transação
    @DeleteMapping("/{id}")
    @Operation(summary = "Remover transação", description = "Exclui uma transação do usuário autenticado")
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