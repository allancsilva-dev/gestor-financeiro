package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.MetaRequest;
import com.gestor.financeiro.dto.MetaResponseDto;
import com.gestor.financeiro.dto.ValorRequest;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.MetaService;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metas")
@Tag(name = "Metas", description = "Gestão de metas financeiras")
public class MetaController {
    
    @Autowired
    private MetaService metaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // GET /api/metas/minhas - Lista metas do usuário autenticado
    @GetMapping("/minhas")
    public ResponseEntity<Page<MetaResponseDto>> listar(
        @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        Page<Meta> metas = metaService.listarPorUsuario(usuarioId, cappedPageable);
        return ResponseEntity.ok(metas.map(MetaResponseDto::fromEntity));
    }
    
    // GET /api/metas/{id} - Busca meta por ID
    @GetMapping("/{id}")
    public ResponseEntity<MetaResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta meta = metaService.buscarPorIdDoUsuario(id, usuarioId);
        return ResponseEntity.ok(MetaResponseDto.fromEntity(meta));
    }
    
    // GET /api/metas/{id}/progresso - Calcula progresso da meta
    @GetMapping("/{id}/progresso")
    public ResponseEntity<Map<String, Object>> calcularProgresso(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta meta = metaService.buscarPorIdDoUsuario(id, usuarioId);
        BigDecimal progresso = metaService.calcularProgresso(id, usuarioId);
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("metaId", id);
        resultado.put("valorTotal", meta.getValorTotal());
        resultado.put("valorReservado", meta.getValorReservado());
        resultado.put("valorRestante", meta.getValorTotal().subtract(meta.getValorReservado()));
        resultado.put("progresso", progresso); // Porcentagem
        
        return ResponseEntity.ok(resultado);
    }
    
    // POST /api/metas - Cria nova meta
    @PostMapping
    public ResponseEntity<MetaResponseDto> criar(@Valid @RequestBody MetaRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta meta = toEntity(request);
        Meta metaCriada = metaService.criar(meta, usuarioId);
        return ResponseEntity.ok(MetaResponseDto.fromEntity(metaCriada));
    }
    
    // PUT /api/metas/{id} - Atualiza meta
    @PutMapping("/{id}")
    public ResponseEntity<MetaResponseDto> atualizar(
        @PathVariable Long id, 
        @Valid @RequestBody MetaRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Meta meta = toEntity(request);
        Meta metaAtualizada = metaService.atualizar(id, meta, usuarioId);
        return ResponseEntity.ok(MetaResponseDto.fromEntity(metaAtualizada));
    }
    
    // PUT /api/metas/{id}/adicionar - Adiciona valor à meta
    @PutMapping("/{id}/adicionar")
    public ResponseEntity<MetaResponseDto> adicionarValor(
        @PathVariable Long id, 
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        Meta meta = metaService.adicionarValor(id, valor, usuarioId);
        return ResponseEntity.ok(MetaResponseDto.fromEntity(meta));
    }
    
    // PUT /api/metas/{id}/remover - Remove valor da meta
    @PutMapping("/{id}/remover")
    public ResponseEntity<MetaResponseDto> removerValor(
        @PathVariable Long id, 
        @Valid @RequestBody ValorRequest request
    ) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        BigDecimal valor = request.getValor();
        Meta meta = metaService.removerValor(id, valor, usuarioId);
        return ResponseEntity.ok(MetaResponseDto.fromEntity(meta));
    }
    
    // DELETE /api/metas/{id} - Deleta meta
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        metaService.deletar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    private Meta toEntity(MetaRequest request) {
        Meta meta = new Meta();
        meta.setNome(request.getNome());
        meta.setValorTotal(request.getValorTotal());
        meta.setValorMensal(request.getValorMensal());
        meta.setDataPrevista(request.getDataLimite());
        meta.setCor(request.getCor());
        meta.setIcone(request.getIcone());
        meta.setDescricao(request.getDescricao());
        return meta;
    }
}