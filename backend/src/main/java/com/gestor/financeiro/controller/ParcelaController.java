package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Parcela;
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
    
    // GET /api/parcelas/transacao/{transacaoId} - Lista parcelas de uma transação
    @GetMapping("/transacao/{transacaoId}")
    public ResponseEntity<List<Parcela>> listarPorTransacao(@PathVariable Long transacaoId) {
        List<Parcela> parcelas = parcelaService.listarPorTransacao(transacaoId);
        return ResponseEntity.ok(parcelas);
    }
    
    // GET /api/parcelas/{id} - Busca parcela por ID
    @GetMapping("/{id}")
    public ResponseEntity<Parcela> buscarPorId(@PathVariable Long id) {
        Parcela parcela = parcelaService.buscarPorId(id);
        return ResponseEntity.ok(parcela);
    }
    
    // PUT /api/parcelas/{id}/pagar - Marca parcela como paga
    @PutMapping("/{id}/pagar")
    public ResponseEntity<Parcela> marcarComoPaga(@PathVariable Long id) {
        Parcela parcela = parcelaService.marcarComoPaga(id);
        return ResponseEntity.ok(parcela);
    }
    
    // PUT /api/parcelas/{id}/despagar - Marca parcela como pendente
    @PutMapping("/{id}/despagar")
    public ResponseEntity<Parcela> marcarComoPendente(@PathVariable Long id) {
        Parcela parcela = parcelaService.marcarComoPendente(id);
        return ResponseEntity.ok(parcela);
    }
}