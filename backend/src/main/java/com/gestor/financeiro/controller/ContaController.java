package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.ContaRequest;
import com.gestor.financeiro.dto.ContaResponseDto;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ContaService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contas")
@Tag(name = "Contas", description = "Gestão de contas bancárias e cartões")
public class ContaController {
    
    @Autowired
    private ContaService contaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/contas/minhas - Lista contas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<Page<ContaResponseDto>> listar(
        @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Conta> contas = contaService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(contas.map(ContaResponseDto::fromEntity));
    }
    
    // GET /api/contas/{id} - Busca conta por ID
    @GetMapping("/{id}")
    public ResponseEntity<ContaResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Conta conta = contaService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(ContaResponseDto.fromEntity(conta));
    }
    
    // POST /api/contas - Cria nova conta
    @PostMapping
    public ResponseEntity<ContaResponseDto> criar(@Valid @RequestBody ContaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Conta conta = toEntity(request);
        Conta contaCriada = contaService.criar(conta, usuarioId);
        return ResponseEntity.ok(ContaResponseDto.fromEntity(contaCriada));
    }
    
    // PUT /api/contas/{id} - Atualiza conta
    @PutMapping("/{id}")
    public ResponseEntity<ContaResponseDto> atualizar(
        @PathVariable Long id, 
        @Valid @RequestBody ContaRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Conta conta = toEntity(request);
        Conta contaAtualizada = contaService.atualizar(id, conta, usuarioId);
        return ResponseEntity.ok(ContaResponseDto.fromEntity(contaAtualizada));
    }
    
    // DELETE /api/contas/{id} - Deleta conta
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        contaService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    private Conta toEntity(ContaRequest request) {
        Conta conta = new Conta();
        conta.setNome(request.getNome());
        conta.setTipo(request.getTipo());
        conta.setLimiteTotal(request.getLimiteTotal());
        conta.setDiaFechamento(request.getDiaFechamento());
        conta.setDiaVencimento(request.getDiaVencimento());
        conta.setCor(request.getCor());
        return conta;
    }
}