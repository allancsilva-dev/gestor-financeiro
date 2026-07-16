package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.AjusteCarteiraRequest;
import com.gestor.financeiro.dto.ContaFinanceiraRequest;
import com.gestor.financeiro.dto.ContaFinanceiraResponse;
import com.gestor.financeiro.dto.MovimentoCarteiraResponse;
import com.gestor.financeiro.dto.ReconciliacaoCarteiraResponse;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.exception.BusinessException;
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
    public ResponseEntity<Page<ContaFinanceiraResponse>> listar(
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Carteira> contas = carteiraService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(contas.map(ContaFinanceiraResponse::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaFinanceiraResponse> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira conta = carteiraService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(ContaFinanceiraResponse.fromEntity(conta));
    }

    @PostMapping
    public ResponseEntity<ContaFinanceiraResponse> criar(@Valid @RequestBody ContaFinanceiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        validarGerenciavelManualmente(request);
        Carteira conta = carteiraService.criar(toEntity(request), usuarioId);
        return ResponseEntity.ok(ContaFinanceiraResponse.fromEntity(conta));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaFinanceiraResponse> atualizar(
            @PathVariable Long id, @Valid @RequestBody ContaFinanceiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        validarGerenciavelManualmente(request);
        Carteira atual = carteiraService.buscarPorIdDoUsuario(id, usuarioId);
        if (!SUBTIPOS_MANUAIS.contains(atual.getSubtipo())) {
            throw new BusinessException("Conta gerenciada pelo módulo de origem é somente leitura");
        }
        Carteira conta = carteiraService.atualizar(id, toEntity(request), usuarioId);
        return ResponseEntity.ok(ContaFinanceiraResponse.fromEntity(conta));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        validarContaGerenciavel(id, usuarioId);
        carteiraService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ajustes")
    public ResponseEntity<ContaFinanceiraResponse> ajustarSaldo(
            @PathVariable Long id, @Valid @RequestBody AjusteCarteiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        validarContaGerenciavel(id, usuarioId);
        Carteira conta = carteiraService.ajustarSaldo(
                id, request.getTipo(), request.getValor(), request.getDescricao(), usuarioId);
        return ResponseEntity.ok(ContaFinanceiraResponse.fromEntity(conta));
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

    private static final java.util.Set<SubtipoContaFinanceira> SUBTIPOS_MANUAIS = java.util.EnumSet.of(
            SubtipoContaFinanceira.DINHEIRO, SubtipoContaFinanceira.CORRENTE,
            SubtipoContaFinanceira.POUPANCA, SubtipoContaFinanceira.PAGAMENTO);

    private void validarGerenciavelManualmente(ContaFinanceiraRequest request) {
        if (request.natureza() != NaturezaContaFinanceira.ATIVO || !SUBTIPOS_MANUAIS.contains(request.subtipo())) {
            throw new BusinessException("Criação manual permitida apenas para contas ATIVO de caixa");
        }
        if (request.subtipo().naturezaPadrao() != request.natureza()) {
            throw new BusinessException("Natureza incompatível com o subtipo");
        }
    }

    private void validarContaGerenciavel(Long id, Long usuarioId) {
        Carteira conta = carteiraService.buscarPorIdDoUsuario(id, usuarioId);
        if (!SUBTIPOS_MANUAIS.contains(conta.getSubtipo())) {
            throw new BusinessException("Conta gerenciada pelo módulo de origem é somente leitura");
        }
    }

    private Carteira toEntity(ContaFinanceiraRequest request) {
        Carteira carteira = new Carteira();
        carteira.setNome(request.nome());
        carteira.setNatureza(request.natureza());
        carteira.setSubtipo(request.subtipo());
        carteira.setLiquidez(request.liquidez());
        carteira.setMoeda(request.moeda());
        carteira.setSaldo(request.saldoInicial());
        carteira.setBanco(request.banco());
        switch (request.subtipo()) {
            case DINHEIRO, CORRENTE, PAGAMENTO, POUPANCA -> { }
            default -> throw new BusinessException("Subtipo não pode ser criado manualmente");
        }
        return carteira;
    }
}
