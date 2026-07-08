package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.MovimentacaoAtivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovimentacaoAtivoRepository extends JpaRepository<MovimentacaoAtivo, Long> {
    List<MovimentacaoAtivo> findByAtivoIdAndUsuarioIdOrderByDataDesc(Long ativoId, Long usuarioId);
}
