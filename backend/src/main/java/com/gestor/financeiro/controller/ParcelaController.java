package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.ParcelaResponseDto;
import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ParcelaService;
import com.gestor.financeiro.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/parcelas")
public class ParcelaController {
    
    @Autowired
    private ParcelaService parcelaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/parcelas/transacao/{transacaoId} - Lista parcelas de uma transação
    @GetMapping("/transacao/{transacaoId}")
    public ResponseEntity<Page<ParcelaResponseDto>> listarPorTransacao(
        @PathVariable Long transacaoId,
        @PageableDefault(size = 20, sort = "numeroParcela", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Parcela> parcelas = parcelaService.listarPorTransacao(transacaoId, usuarioId, cappedPageable);
        return ResponseEntity.ok(parcelas.map(ParcelaResponseDto::fromEntity));
    }
    
    // GET /api/parcelas/{id} - Busca parcela por ID
    @GetMapping("/{id}")
    public ResponseEntity<ParcelaResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Parcela parcela = parcelaService.buscarPorId(id, usuarioId);
        return ResponseEntity.ok(ParcelaResponseDto.fromEntity(parcela));
    }
    
    // PUT /api/parcelas/{id}/pagar - Marca parcela como paga
    @PutMapping("/{id}/pagar")
    public ResponseEntity<ParcelaResponseDto> marcarComoPaga(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Parcela parcela = parcelaService.marcarComoPaga(id, usuarioId);
        return ResponseEntity.ok(ParcelaResponseDto.fromEntity(parcela));
    }
    
    // PUT /api/parcelas/{id}/despagar - Marca parcela como pendente
    @PutMapping("/{id}/despagar")
    public ResponseEntity<ParcelaResponseDto> marcarComoPendente(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Parcela parcela = parcelaService.marcarComoPendente(id, usuarioId);
        return ResponseEntity.ok(ParcelaResponseDto.fromEntity(parcela));
    }
}