package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ContaFixa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaFixaRepository extends JpaRepository<ContaFixa, Long> {
    
    // Busca contas fixas ATIVAS de um usuário
    // Query: SELECT * FROM contas_fixas WHERE usuario_id = ? AND ativo = true
    List<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // Busca contas fixas ativas com paginação.
    Page<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);

    Optional<ContaFixa> findByIdAndUsuarioId(Long id, Long usuarioId);
}