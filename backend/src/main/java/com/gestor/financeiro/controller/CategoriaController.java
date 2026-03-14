package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.CategoriaCreateRequest;
import com.gestor.financeiro.dto.CategoriaResponseDto;
import com.gestor.financeiro.dto.CategoriaUpdateRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.CategoriaService;
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

@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorias", description = "Gestão de categorias financeiras")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    // Lista categorias do usuário logado
    @GetMapping("/minhas")
    public ResponseEntity<Page<CategoriaResponseDto>> listarMinhas(
        @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Pageable cappedPageable = PaginationUtils.enforceMaxSize(pageable, 100);
        return ResponseEntity.ok(categoriaService.listarMinhasCategorias(cappedPageable).map(CategoriaResponseDto::fromEntity));
    }

    // GET /api/categorias/{id} - Busca categoria por ID com validação de ownership
    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponseDto> buscarPorId(@PathVariable Long id) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        return ResponseEntity.ok(CategoriaResponseDto.fromEntity(categoriaService.buscarPorIdDoUsuario(id, usuarioId)));
    }

    // Criar categoria
    @PostMapping
    public ResponseEntity<CategoriaResponseDto> criar(@Valid @RequestBody CategoriaCreateRequest request) {
        Categoria categoria = new Categoria();
        categoria.setNome(request.nome());
        categoria.setCor(request.cor());
        categoria.setIcone(request.icone());
        categoria.setValorEsperado(request.valorEsperado());

        Categoria categoriaCriada = categoriaService.criar(categoria);
        return ResponseEntity.ok(CategoriaResponseDto.fromEntity(categoriaCriada));
    }

    // Atualizar categoria
    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDto> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaUpdateRequest request
    ) {
        Categoria categoriaAtualizada = new Categoria();
        categoriaAtualizada.setNome(request.nome());
        categoriaAtualizada.setCor(request.cor());
        categoriaAtualizada.setIcone(request.icone());
        categoriaAtualizada.setValorEsperado(request.valorEsperado());

        return ResponseEntity.ok(CategoriaResponseDto.fromEntity(categoriaService.atualizar(id, categoriaAtualizada)));
    }

    // Deletar (inativar)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
