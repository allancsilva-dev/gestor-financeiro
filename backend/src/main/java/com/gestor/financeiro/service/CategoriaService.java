package com.gestor.financeiro.service;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoriaService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    // ✅ PEGA O USUÁRIO LOGADO DO TOKEN
    private Usuario getUsuarioLogado() {
        // Pega o email do usuário autenticado (vem do JWT)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        
        // Busca o usuário no banco pelo email
        return usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }
    
    // Lista categorias ativas do usuário LOGADO
    public List<Categoria> listarMinhasCategorias() {
        Usuario usuario = getUsuarioLogado();
        return categoriaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId());
    }
    
    // Lista categorias de um usuário específico (admin)
    public List<Categoria> listarPorUsuario(Long usuarioId) {
        return categoriaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
    
    // ✅ CORRIGIDO - Pega usuário do TOKEN
    public Categoria criar(Categoria categoria) {
        // Pega o usuário autenticado do token
        Usuario usuario = getUsuarioLogado();
        
        // Associa o usuário à categoria
        categoria.setUsuario(usuario);
        
        // Inicializa valores padrão
        if (categoria.getAtivo() == null) {
            categoria.setAtivo(true);
        }
        
        return categoriaRepository.save(categoria);
    }
    
    // Atualiza categoria existente
    public Categoria atualizar(Long id, Categoria categoriaAtualizada) {
        Usuario usuario = getUsuarioLogado();
        
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        
        // Verifica se a categoria pertence ao usuário logado
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("Você não tem permissão para editar esta categoria");
        }
        
        categoria.setNome(categoriaAtualizada.getNome());
        categoria.setCor(categoriaAtualizada.getCor());
        categoria.setIcone(categoriaAtualizada.getIcone());
        categoria.setValorEsperado(categoriaAtualizada.getValorEsperado());
        
        return categoriaRepository.save(categoria);
    }
    
    // "Deleta" categoria (só marca como inativa)
    public void deletar(Long id) {
        Usuario usuario = getUsuarioLogado();
        
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        
        // Verifica se a categoria pertence ao usuário logado
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("Você não tem permissão para deletar esta categoria");
        }
        
        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
    }
    
    // Busca categoria por ID
    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
    }
}