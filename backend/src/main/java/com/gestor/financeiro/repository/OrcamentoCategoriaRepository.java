package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.OrcamentoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrcamentoCategoriaRepository extends JpaRepository<OrcamentoCategoria, Long> {

    List<OrcamentoCategoria> findByOrcamentoIdAndAtivoTrue(Long orcamentoId);

    void deleteByOrcamentoId(Long orcamentoId);
}
