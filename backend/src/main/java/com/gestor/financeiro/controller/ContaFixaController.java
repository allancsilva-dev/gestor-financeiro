package com.gestor.financeiro.controller;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.ContaFixaResponseDto;
import com.gestor.financeiro.dto.ContaFixaRequest;
import com.gestor.financeiro.dto.ValorRequest;
import com.gestor.financeiro.dto.ExecucaoRecorrenciaDto;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.ContaFixaService;
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
@RequestMapping("/api/v1/contas-fixas")
@Tag(name = "Contas Fixas", description = "Gestão de despesas recorrentes")
@RequiredArgsConstructor
public class ContaFixaController {
    private final ContaFixaService contaFixaService;
    private final AuthenticatedUserService authenticatedUserService;

    // GET /api/contas-fixas/minhas - Lista contas fixas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<Page<ContaFixaResponseDto>> listarPorUsuario(
        @PageableDefault(size = 20, sort = "diaVencimento", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<ContaFixa> contas = contaFixaService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(contas.map(ContaFixaResponseDto::fromEntity));
    }

    // GET /api/contas-fixas/{id} - Busca conta fixa por ID
    @GetMapping("/{id}")
    public ResponseEntity<ContaFixaResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa conta = contaFixaService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(conta));
    }

    // POST /api/contas-fixas - Cria nova conta fixa
    @PostMapping
    public ResponseEntity<ContaFixaResponseDto> criar(@Valid @RequestBody ContaFixaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa contaFixa = toEntity(request);
        ContaFixa novaConta = contaFixaService.criar(contaFixa, usuarioId);
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(novaConta));
    }

    // PUT /api/contas-fixas/{id} - Atualiza conta fixa
    @PutMapping("/{id}")
    public ResponseEntity<ContaFixaResponseDto> atualizar(@PathVariable Long id, @Valid @RequestBody ContaFixaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa contaFixa = toEntity(request);
        ContaFixa contaAtualizada = contaFixaService.atualizar(id, contaFixa, usuarioId);
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(contaAtualizada));
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
    public ResponseEntity<ContaFixaResponseDto> marcarComoPaga(
        @PathVariable Long id,
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valorPago = request.getValor();
        ContaFixa contaPaga = contaFixaService.marcarComoPaga(id, valorPago, request.getCarteiraId(), usuarioId);
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(contaPaga));
    }

    @PutMapping("/{id}/realizar")
    public ResponseEntity<ContaFixaResponseDto> realizar(
            @PathVariable Long id, @Valid @RequestBody ValorRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(
                contaFixaService.realizar(id, request.getValor(), request.getCarteiraId(), usuarioId, false)));
    }

    @GetMapping("/falhas-pendentes")
    public ResponseEntity<List<ExecucaoRecorrenciaDto>> listarFalhasPendentes() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(contaFixaService.listarFalhasPendentes(usuarioId).stream()
                .map(ExecucaoRecorrenciaDto::fromEntity).toList());
    }

    @PutMapping("/{id}/pular")
    public ResponseEntity<ContaFixaResponseDto> pularMes(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa contaAtualizada = contaFixaService.pularMes(id, usuarioId);
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(contaAtualizada));
    }

    @PutMapping("/{id}/reativar")
    public ResponseEntity<ContaFixaResponseDto> reativar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        ContaFixa contaAtualizada = contaFixaService.reativar(id, usuarioId);
        return ResponseEntity.ok(ContaFixaResponseDto.fromEntity(contaAtualizada));
    }

    private ContaFixa toEntity(ContaFixaRequest request) {
        ContaFixa contaFixa = new ContaFixa();
        contaFixa.setNome(request.getDescricao());
        contaFixa.setValorPlanejado(request.getValor());
        contaFixa.setDiaVencimento(request.getDiaVencimento());
        contaFixa.setRecorrente(request.getRecorrente());
        contaFixa.setObservacoes(request.getObservacoes());
        contaFixa.setTipo(request.getTipo());
        contaFixa.setExecucaoAutomatica(request.getExecucaoAutomatica());
        if (request.getCarteiraId() != null) {
            Carteira carteira = new Carteira();
            carteira.setId(request.getCarteiraId());
            contaFixa.setCarteira(carteira);
        }

        Categoria categoria = new Categoria();
        categoria.setId(request.getCategoriaIdNormalizada());
        contaFixa.setCategoria(categoria);

        return contaFixa;
    }
}
