package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.repository.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoriaService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    // Busca todas as categorias ATIVAS do usuário
    public List<Categoria> listarPorUsuario(Long usuarioId) {
        return categoriaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
    
    // Cria uma nova categoria
    public Categoria criar(Categoria categoria) {
        // Inicializa valores padrão se não informados
        if (categoria.getAtivo() == null) { // ✅ CORRIGIDO AQUI
            categoria.setAtivo(true);
        }
        return categoriaRepository.save(categoria);
    }
    
    // Atualiza categoria existente
    public Categoria atualizar(Long id, Categoria categoriaAtualizada) {
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        
        categoria.setNome(categoriaAtualizada.getNome());
        categoria.setCor(categoriaAtualizada.getCor());
        categoria.setIcone(categoriaAtualizada.getIcone());
        categoria.setValorEsperado(categoriaAtualizada.getValorEsperado());
        
        return categoriaRepository.save(categoria);
    }
    
    // "Deleta" categoria (só marca como inativa)
    public void deletar(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        
        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
    }
    
    // Busca categoria por ID
    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
    }
}