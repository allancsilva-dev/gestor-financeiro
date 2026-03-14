package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Meta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MetaRepository extends JpaRepository<Meta, Long> {
    
    // Busca metas ATIVAS de um usuário
    // Query gerada: SELECT * FROM metas WHERE usuario_id = ? AND ativa = true
    List<Meta> findByUsuarioIdAndAtivaTrue(Long usuarioId);

    // Busca metas ativas com paginação.
    Page<Meta> findByUsuarioIdAndAtivaTrue(Long usuarioId, Pageable pageable);

    Optional<Meta> findByIdAndUsuarioId(Long id, Long usuarioId);
}