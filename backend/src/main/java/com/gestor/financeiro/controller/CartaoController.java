package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.CartaoRequest;
import com.gestor.financeiro.dto.CartaoResponse;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.enums.TipoConta;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ContaService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cartoes")
@Tag(name = "Cartões", description = "Contrato público canônico de cartões")
@RequiredArgsConstructor
public class CartaoController {
    private final ContaService contaService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping({"", "/meus"})
    public ResponseEntity<Page<CartaoResponse>> listar(
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable capped = PaginationUtils.enforceMaxSize(pageable, 100);
        return ResponseEntity.ok(contaService.listarCartoesPorUsuario(usuarioId, capped).map(CartaoResponse::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CartaoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(CartaoResponse.fromEntity(
                contaService.buscarCartaoDoUsuario(id, authenticatedUserService.getAuthenticatedUserId())));
    }

    @PostMapping
    public ResponseEntity<CartaoResponse> criar(@Valid @RequestBody CartaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(CartaoResponse.fromEntity(contaService.criar(toEntity(request), usuarioId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartaoResponse> atualizar(
            @PathVariable Long id, @Valid @RequestBody CartaoRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(CartaoResponse.fromEntity(contaService.atualizarCartao(id, toEntity(request), usuarioId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        contaService.deletarCartao(id, authenticatedUserService.getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    private Conta toEntity(CartaoRequest request) {
        Conta conta = new Conta();
        conta.setNome(request.nome());
        conta.setTipo(TipoConta.CREDITO);
        conta.setLimiteTotal(request.limiteTotal());
        conta.setDiaFechamento(request.diaFechamento());
        conta.setDiaVencimento(request.diaVencimento());
        conta.setCor(request.cor());
        conta.setBanco(request.banco());
        return conta;
    }
}
