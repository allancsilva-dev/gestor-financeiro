package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.OrcamentoMensal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OrcamentoMensalRepository extends JpaRepository<OrcamentoMensal, Long> {

    Optional<OrcamentoMensal> findByUsuarioIdAndMesAndAno(Long usuarioId, Integer mes, Integer ano);

    Optional<OrcamentoMensal> findByIdAndUsuarioId(Long id, Long usuarioId);
}
