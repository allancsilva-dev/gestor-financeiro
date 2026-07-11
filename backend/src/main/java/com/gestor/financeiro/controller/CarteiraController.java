package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.AjusteCarteiraRequest;
import com.gestor.financeiro.dto.CarteiraResponseDto;
import com.gestor.financeiro.dto.CarteiraRequest;
import com.gestor.financeiro.dto.MovimentoCarteiraResponse;
import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.dto.ValorRequest;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CarteiraService;
import com.gestor.financeiro.service.LedgerReconciliationService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/carteiras")
@Tag(name = "Carteiras", description = "Gestão de carteiras e saldo")
@RequiredArgsConstructor
public class CarteiraController {
    private final CarteiraService carteiraService;
    private final LedgerReconciliationService ledgerReconciliationService;
    private final AuthenticatedUserService authenticatedUserService;
    
    @GetMapping("/minhas")
    public ResponseEntity<Page<CarteiraResponseDto>> listar(
        @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Carteira> carteiras = carteiraService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(carteiras.map(CarteiraResponseDto::fromEntity));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CarteiraResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira carteira = carteiraService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }

    @GetMapping("/minhas/reconciliacao")
    public ResponseEntity<List<ReconciliacaoCarteiraResponse>> reconciliarMinhasCarteiras() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ledgerReconciliationService.reconciliarUsuario(usuarioId));
    }

    @GetMapping("/{id}/reconciliacao")
    public ResponseEntity<ReconciliacaoCarteiraResponse> reconciliarCarteira(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ledgerReconciliationService.reconciliarCarteira(usuarioId, id));
    }

    @GetMapping("/{id}/movimentos")
    public ResponseEntity<Page<MovimentoCarteiraResponse>> listarMovimentos(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "dataMovimento", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<MovimentoCarteira> movimentos = carteiraService.listarMovimentos(id, usuarioId, cappedPageable);
        return ResponseEntity.ok(movimentos.map(MovimentoCarteiraResponse::fromEntity));
    }
    
    @PostMapping
    public ResponseEntity<CarteiraResponseDto> criar(@Valid @RequestBody CarteiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira carteira = toEntity(request);
        Carteira carteiraCriada = carteiraService.criar(carteira, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteiraCriada));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<CarteiraResponseDto> atualizar(
        @PathVariable Long id,
        @Valid @RequestBody CarteiraRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira carteira = toEntity(request);
        Carteira carteiraAtualizada = carteiraService.atualizar(id, carteira, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteiraAtualizada));
    }

    @PostMapping("/{id}/ajustes")
    public ResponseEntity<CarteiraResponseDto> ajustarSaldo(
            @PathVariable Long id,
            @Valid @RequestBody AjusteCarteiraRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira carteira = carteiraService.ajustarSaldo(
                id, request.getTipo(), request.getValor(), request.getDescricao(), usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }

    @PostMapping("/{id}/adicionar")
    @Deprecated(since = "PR-LEDGER-06", forRemoval = false)
    public ResponseEntity<CarteiraResponseDto> adicionarDinheiro(
        @PathVariable Long id,
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        Carteira carteira = carteiraService.adicionarDinheiro(id, valor, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }
    
    @PostMapping("/{id}/remover")
    @Deprecated(since = "PR-LEDGER-06", forRemoval = false)
    public ResponseEntity<CarteiraResponseDto> removerDinheiro(
        @PathVariable Long id,
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        Carteira carteira = carteiraService.removerDinheiro(id, valor, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }
    
    @GetMapping("/minhas/saldo-total")
    public ResponseEntity<BigDecimal> calcularSaldoTotal() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal saldoTotal = carteiraService.calcularSaldoTotal(usuarioId);
        return ResponseEntity.ok(saldoTotal);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        carteiraService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
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
