package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.CategoriaCreateRequest;
import com.gestor.financeiro.dto.CategoriaUpdateRequest;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@CrossOrigin(origins = "*")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    // Lista categorias do usuário logado
    @GetMapping("/minhas")
    public ResponseEntity<List<Categoria>> listarMinhas() {
        return ResponseEntity.ok(categoriaService.listarMinhasCategorias());
    }

    // Lista categorias por ID de usuário (apenas admin)
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Categoria>> listarPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(categoriaService.listarPorUsuario(usuarioId));
    }

    // Criar categoria
    @PostMapping
    public ResponseEntity<Categoria> criar(@RequestBody CategoriaCreateRequest request) {
        Categoria categoria = new Categoria();
        categoria.setNome(request.nome());
        categoria.setCor(request.cor());
        categoria.setIcone(request.icone());
        categoria.setValorEsperado(request.valorEsperado());

        Categoria categoriaCriada = categoriaService.criar(categoria);
        return ResponseEntity.ok(categoriaCriada);
    }

    // Atualizar categoria
    @PutMapping("/{id}")
    public ResponseEntity<Categoria> atualizar(
            @PathVariable Long id,
            @RequestBody CategoriaUpdateRequest request
    ) {
        Categoria categoriaAtualizada = new Categoria();
        categoriaAtualizada.setNome(request.nome());
        categoriaAtualizada.setCor(request.cor());
        categoriaAtualizada.setIcone(request.icone());
        categoriaAtualizada.setValorEsperado(request.valorEsperado());

        return ResponseEntity.ok(categoriaService.atualizar(id, categoriaAtualizada));
    }

    // Deletar (inativar)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
