package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ContaFixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contas-fixas")
public class ContaFixaController {

    @Autowired
    private ContaFixaService contaFixaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    // GET /api/contas-fixas/minhas - Lista contas fixas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<List<ContaFixa>> listarPorUsuario() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        List<ContaFixa> contas = contaFixaService.listarPorUsuario(usuarioId);
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
    public ResponseEntity<ContaFixa> criar(@RequestBody ContaFixa contaFixa) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa novaConta = contaFixaService.criar(contaFixa, usuarioId);
        return ResponseEntity.ok(novaConta);
    }

    // PUT /api/contas-fixas/{id} - Atualiza conta fixa
    @PutMapping("/{id}")
    public ResponseEntity<ContaFixa> atualizar(@PathVariable Long id, @RequestBody ContaFixa contaFixa) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
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
        @RequestBody Map<String, BigDecimal> body
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valorPago = body.get("valorPago");
        ContaFixa contaPaga = contaFixaService.marcarComoPaga(id, valorPago, usuarioId);
        return ResponseEntity.ok(contaPaga);
    }
}