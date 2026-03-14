package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.CarteiraResponseDto;
import com.gestor.financeiro.dto.CarteiraRequest;
import com.gestor.financeiro.dto.ValorRequest;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CarteiraService;
import com.gestor.financeiro.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/carteiras")
@Tag(name = "Carteiras", description = "Gestão de carteiras e saldo")
public class CarteiraController {
    
    @Autowired
    private CarteiraService carteiraService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/carteiras/minhas - Lista carteiras do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<Page<CarteiraResponseDto>> listar(
        @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Carteira> carteiras = carteiraService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(carteiras.map(CarteiraResponseDto::fromEntity));
    }
    
    // GET /api/carteiras/{id} - Busca carteira por ID
    @GetMapping("/{id}")
    public ResponseEntity<CarteiraResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira carteira = carteiraService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }
    
    // POST /api/carteiras - Cria nova carteira
    @PostMapping
    public ResponseEntity<CarteiraResponseDto> criar(@Valid @RequestBody CarteiraRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Carteira carteira = toEntity(request);
        Carteira carteiraCriada = carteiraService.criar(carteira, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteiraCriada));
    }
    
    // PUT /api/carteiras/{id} - Atualiza carteira
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
    
    // POST /api/carteiras/{id}/adicionar - Adiciona dinheiro
    @PostMapping("/{id}/adicionar")
    public ResponseEntity<CarteiraResponseDto> adicionarDinheiro(
        @PathVariable Long id,
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        Carteira carteira = carteiraService.adicionarDinheiro(id, valor, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }
    
    // POST /api/carteiras/{id}/remover - Remove dinheiro
    @PostMapping("/{id}/remover")
    public ResponseEntity<CarteiraResponseDto> removerDinheiro(
        @PathVariable Long id,
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        Carteira carteira = carteiraService.removerDinheiro(id, valor, usuarioId);
        return ResponseEntity.ok(CarteiraResponseDto.fromEntity(carteira));
    }
    
    // GET /api/carteiras/minhas/saldo-total - Calcula saldo total do usuário autenticado
    @GetMapping("/minhas/saldo-total")
    public ResponseEntity<BigDecimal> calcularSaldoTotal() {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal saldoTotal = carteiraService.calcularSaldoTotal(usuarioId);
        return ResponseEntity.ok(saldoTotal);
    }
    
    // DELETE /api/carteiras/{id} - Deleta carteira
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