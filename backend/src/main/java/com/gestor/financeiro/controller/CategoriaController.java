package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController // Controller REST (retorna JSON)
@RequestMapping("/api/categorias") // Rota base: /api/categorias
@CrossOrigin(origins = "*") // Permite frontend acessar
public class CategoriaController {
    
    @Autowired
    private CategoriaService categoriaService;
    
    // GET /api/categorias/usuario/{usuarioId} - Lista categorias do usuário
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Categoria>> listar(@PathVariable Long usuarioId) {
        List<Categoria> categorias = categoriaService.listarPorUsuario(usuarioId);
        return ResponseEntity.ok(categorias);
    }
    
    // GET /api/categorias/{id} - Busca categoria por ID
    @GetMapping("/{id}")
    public ResponseEntity<Categoria> buscarPorId(@PathVariable Long id) {
        Categoria categoria = categoriaService.buscarPorId(id);
        return ResponseEntity.ok(categoria);
    }
    
    // POST /api/categorias - Cria nova categoria
    @PostMapping
    public ResponseEntity<Categoria> criar(@RequestBody Categoria categoria) {
        Categoria categoriaCriada = categoriaService.criar(categoria);
        return ResponseEntity.ok(categoriaCriada);
    }
    
    // PUT /api/categorias/{id} - Atualiza categoria
    @PutMapping("/{id}")
    public ResponseEntity<Categoria> atualizar(
        @PathVariable Long id, 
        @RequestBody Categoria categoria
    ) {
        Categoria categoriaAtualizada = categoriaService.atualizar(id, categoria);
        return ResponseEntity.ok(categoriaAtualizada);
    }
    
    // DELETE /api/categorias/{id} - Deleta categoria
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}