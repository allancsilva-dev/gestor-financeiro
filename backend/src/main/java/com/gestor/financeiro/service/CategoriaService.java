package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.gestor.financeiro.security.AuthenticatedUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoriaService {
    
    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;
    
    // Lista categorias ativas do usuário LOGADO
    public Page<Categoria> listarMinhasCategorias(Pageable pageable) {
        Usuario usuario = authenticatedUserService.getAuthenticatedUser();
        return categoriaRepository.findByUsuarioIdAndAtivoTrue(usuario.getId(), pageable);
    }
    
    // ✅ CORRIGIDO - Pega usuário do TOKEN
    public Categoria criar(Categoria categoria) {
        // Pega o usuário autenticado do token
        Usuario usuario = authenticatedUserService.getAuthenticatedUser();
        
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
        Usuario usuario = authenticatedUserService.getAuthenticatedUser();
        
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        
        // Verifica se a categoria pertence ao usuário logado
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new BusinessException("Você não tem permissão para editar esta categoria");
        }
        
        categoria.setNome(categoriaAtualizada.getNome());
        categoria.setCor(categoriaAtualizada.getCor());
        categoria.setIcone(categoriaAtualizada.getIcone());
        categoria.setValorEsperado(categoriaAtualizada.getValorEsperado());
        
        return categoriaRepository.save(categoria);
    }
    
    // "Deleta" categoria (só marca como inativa)
    public void deletar(Long id) {
        Usuario usuario = authenticatedUserService.getAuthenticatedUser();
        
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        
        // Verifica se a categoria pertence ao usuário logado
        if (!categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new BusinessException("Você não tem permissão para deletar esta categoria");
        }
        
        categoria.setAtivo(false);
        categoriaRepository.save(categoria);
    }
    
    // Busca categoria por ID
    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
    }

    public Categoria buscarPorIdDoUsuario(Long id, Long usuarioId) {
        Categoria categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

        if (!categoria.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedAccessException("Acesso negado a esta categoria");
        }

        return categoria;
    }
}