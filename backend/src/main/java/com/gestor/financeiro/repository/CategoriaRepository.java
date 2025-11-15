package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    
    // Busca categorias ATIVAS de um usuário
    List<Categoria> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    
    // Busca TODAS as categorias do usuário (ativas ou não)
    List<Categoria> findByUsuarioId(Long usuarioId);
}