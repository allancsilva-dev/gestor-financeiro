package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.service.ContaFixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.gestor.financeiro.repository.UsuarioRepository;
//import com.gestor.financeiro.model.Usuario;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contas-fixas")
public class ContaFixaController {

    @Autowired
    private ContaFixaService contaFixaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // GET /api/contas-fixas/usuario/{usuarioId} - Lista contas fixas do usuário
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<ContaFixa>> listarPorUsuario(@PathVariable Long usuarioId) {
        List<ContaFixa> contas = contaFixaService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(contas);
    }

    // GET /api/contas-fixas/{id} - Busca conta fixa por ID
    @GetMapping("/{id}")
    public ResponseEntity<ContaFixa> buscarPorId(@PathVariable Long id) {
        ContaFixa conta = contaFixaService.buscarPorId(id);
        return ResponseEntity.ok(conta);
    }

    // POST /api/contas-fixas - Cria nova conta fixa
    @PostMapping
    public ResponseEntity<ContaFixa> criar(@RequestBody ContaFixa contaFixa) {
        ContaFixa novaConta = contaFixaService.criar(contaFixa);
        return ResponseEntity.ok(novaConta);
    }

    // PUT /api/contas-fixas/{id} - Atualiza conta fixa
    @PutMapping("/{id}")
    public ResponseEntity<ContaFixa> atualizar(@PathVariable Long id, @RequestBody ContaFixa contaFixa) {
        ContaFixa contaAtualizada = contaFixaService.atualizar(id, contaFixa);
        return ResponseEntity.ok(contaAtualizada);
    }

    // DELETE /api/contas-fixas/{id} - Deleta (desativa) conta fixa
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        contaFixaService.deletar(id);
        return ResponseEntity.ok().build();
    }

    // PUT /api/contas-fixas/{id}/pagar - Marca conta como paga
    @PutMapping("/{id}/pagar")
    public ResponseEntity<ContaFixa> marcarComoPaga(
        @PathVariable Long id,
        @RequestBody Map<String, BigDecimal> body
    ) {
        BigDecimal valorPago = body.get("valorPago");
        ContaFixa contaPaga = contaFixaService.marcarComoPaga(id, valorPago);
        return ResponseEntity.ok(contaPaga);
    }
}