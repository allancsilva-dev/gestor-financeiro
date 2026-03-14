package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    // Busca categorias ATIVAS de um usuário
    List<Categoria> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // Busca categorias ativas com paginação.
    Page<Categoria> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);
    
    // Busca TODAS as categorias do usuário (ativas ou não)
    List<Categoria> findByUsuarioId(Long usuarioId);

    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);
}