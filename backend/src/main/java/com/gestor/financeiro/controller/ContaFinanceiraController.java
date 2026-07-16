package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.AjusteCarteiraRequest;
import com.gestor.financeiro.dto.CarteiraRequest;
import com.gestor.financeiro.dto.CarteiraResponseDto;
import com.gestor.financeiro.dto.MovimentoCarteiraResponse;
import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CarteiraService;
import com.gestor.financeiro.service.LedgerReconciliationService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rota aditiva da conta financeira unificada (ADR-0008, PR-F2-02).
 * Mesmo recurso fisico de /api/v1/carteiras, sob o nome canonico do dominio.
 * A rota legada permanece ate o contract (PR-F2-19).
 */
@RestController
@RequestMapping("/api/v1/contas-financeiras")
@Tag(name = "Contas financeiras", description = "Conta financeira unificada (ADR-0008)")
@RequiredArgsConstructor
public class ContaFinanceiraController {

    private final CarteiraService carteiraService;
    private final LedgerReconciliationService ledgerReconciliationService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/minhas")
    public ResponseEntity<Page<CarteiraResponseDto>> listar(
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Carteira> contas = carteiraService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(contas.map(CarteiraResponseDto::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarteiraResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira conta = carteiraService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(conta));
    }

    @PostMapping
    public ResponseEntity<CarteiraResponseDto> criar(@Valid @RequestBody CarteiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira conta = carteiraService.criar(toEntity(request), usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(conta));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CarteiraResponseDto> atualizar(
            @PathVariable Long id, @Valid @RequestBody CarteiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira conta = carteiraService.atualizar(id, toEntity(request), usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(conta));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        carteiraService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ajustes")
    public ResponseEntity<CarteiraResponseDto> ajustarSaldo(
            @PathVariable Long id, @Valid @RequestBody AjusteCarteiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira conta = carteiraService.ajustarSaldo(
                id, request.getTipo(), request.getValor(), request.getDescricao(), usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(conta));
    }

    @GetMapping("/{id}/movimentos")
    public ResponseEntity<Page<MovimentoCarteiraResponse>> listarMovimentos(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "dataMovimento", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<MovimentoCarteira> movimentos =
                carteiraService.listarMovimentos(id, usuarioId, cappedPageable);
        return ResponseEntity.ok(movimentos.map(MovimentoCarteiraResponse::fromEntity));
    }

    @GetMapping("/minhas/reconciliacao")
    public ResponseEntity<List<ReconciliacaoCarteiraResponse>> reconciliarMinhasContas() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ledgerReconciliationService.reconciliarUsuario(usuarioId));
    }

    @GetMapping("/{id}/reconciliacao")
    public ResponseEntity<ReconciliacaoCarteiraResponse> reconciliarConta(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ledgerReconciliationService.reconciliarCarteira(usuarioId, id));
    }

    private Carteira toEntity(CarteiraRequest request) {
        Carteira carteira = new Carteira();
        carteira.setNome(request.getNome());
        carteira.setTipo(request.getTipo());
        carteira.setSaldo(request.getSaldo());
        carteira.setBanco(request.getBanco());
        return carteira;
    }
}
