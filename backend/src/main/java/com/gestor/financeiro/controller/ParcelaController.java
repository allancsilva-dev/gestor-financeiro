package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ParcelaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/parcelas")
public class ParcelaController {
    
    @Autowired
    private ParcelaService parcelaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/parcelas/transacao/{transacaoId} - Lista parcelas de uma transação
    @GetMapping("/transacao/{transacaoId}")
    public ResponseEntity<List<Parcela>> listarPorTransacao(@PathVariable Long transacaoId) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        List<Parcela> parcelas = parcelaService.listarPorTransacao(transacaoId, usuarioId);
        return ResponseEntity.ok(parcelas);
    }
    
    // GET /api/parcelas/{id} - Busca parcela por ID
    @GetMapping("/{id}")
    public ResponseEntity<Parcela> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Parcela parcela = parcelaService.buscarPorId(id, usuarioId);
        return ResponseEntity.ok(parcela);
    }
    
    // PUT /api/parcelas/{id}/pagar - Marca parcela como paga
    @PutMapping("/{id}/pagar")
    public ResponseEntity<Parcela> marcarComoPaga(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Parcela parcela = parcelaService.marcarComoPaga(id, usuarioId);
        return ResponseEntity.ok(parcela);
    }
    
    // PUT /api/parcelas/{id}/despagar - Marca parcela como pendente
    @PutMapping("/{id}/despagar")
    public ResponseEntity<Parcela> marcarComoPendente(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Parcela parcela = parcelaService.marcarComoPendente(id, usuarioId);
        return ResponseEntity.ok(parcela);
    }
}