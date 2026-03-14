package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.ContaFixaRequest;
import com.gestor.financeiro.dto.ValorRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ContaFixaService;
import com.gestor.financeiro.util.PaginationUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/contas-fixas")
public class ContaFixaController {

    @Autowired
    private ContaFixaService contaFixaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    // GET /api/contas-fixas/minhas - Lista contas fixas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<Page<ContaFixa>> listarPorUsuario(
        @PageableDefault(size = 20, sort = "diaVencimento", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<ContaFixa> contas = contaFixaService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(contas);
    }

    // GET /api/contas-fixas/{id} - Busca conta fixa por ID
    @GetMapping("/{id}")
    public ResponseEntity<ContaFixa> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa conta = contaFixaService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(conta);
    }

    // POST /api/contas-fixas - Cria nova conta fixa
    @PostMapping
    public ResponseEntity<ContaFixa> criar(@Valid @RequestBody ContaFixaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa contaFixa = toEntity(request);
        ContaFixa novaConta = contaFixaService.criar(contaFixa, usuarioId);
        return ResponseEntity.ok(novaConta);
    }

    // PUT /api/contas-fixas/{id} - Atualiza conta fixa
    @PutMapping("/{id}")
    public ResponseEntity<ContaFixa> atualizar(@PathVariable Long id, @Valid @RequestBody ContaFixaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa contaFixa = toEntity(request);
        ContaFixa contaAtualizada = contaFixaService.atualizar(id, contaFixa, usuarioId);
        return ResponseEntity.ok(contaAtualizada);
    }

    // DELETE /api/contas-fixas/{id} - Deleta (desativa) conta fixa
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        contaFixaService.deletar(id, usuarioId);
        return ResponseEntity.ok().build();
    }

    // PUT /api/contas-fixas/{id}/pagar - Marca conta como paga
    @PutMapping("/{id}/pagar")
    public ResponseEntity<ContaFixa> marcarComoPaga(
        @PathVariable Long id,
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valorPago = request.getValor();
        ContaFixa contaPaga = contaFixaService.marcarComoPaga(id, valorPago, usuarioId);
        return ResponseEntity.ok(contaPaga);
    }

    private ContaFixa toEntity(ContaFixaRequest request) {
        ContaFixa contaFixa = new ContaFixa();
        contaFixa.setNome(request.getDescricao());
        contaFixa.setValorPlanejado(request.getValor());
        contaFixa.setDiaVencimento(request.getDiaVencimento());
        contaFixa.setRecorrente(request.getRecorrente());
        contaFixa.setObservacoes(request.getObservacoes());

        Categoria categoria = new Categoria();
        categoria.setId(request.getCategoriaIdNormalizada());
        contaFixa.setCategoria(categoria);

        return contaFixa;
    }
}