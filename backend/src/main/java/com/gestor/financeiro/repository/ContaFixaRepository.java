package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ContaFixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaFixaRepository extends JpaRepository<ContaFixa, Long> {
    
    // Busca contas fixas ATIVAS de um usuário
    // Query: SELECT * FROM contas_fixas WHERE usuario_id = ? AND ativo = true
    List<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    Optional<ContaFixa> findByIdAndUsuarioId(Long id, Long usuarioId);
}