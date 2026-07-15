package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.CarteiraResponseDto;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CarteiraService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
