package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Meta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MetaRepository extends JpaRepository<Meta, Long> {
    
    // Busca metas ATIVAS de um usuário
    // Query gerada: SELECT * FROM metas WHERE usuario_id = ? AND ativa = true
    List<Meta> findByUsuarioIdAndAtivaTrue(Long usuarioId);
}