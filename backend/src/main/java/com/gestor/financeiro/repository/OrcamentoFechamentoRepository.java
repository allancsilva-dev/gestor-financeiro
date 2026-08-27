package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.OrcamentoFechamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrcamentoFechamentoRepository extends JpaRepository<OrcamentoFechamento, Long> {

    Optional<OrcamentoFechamento> findByUsuarioIdAndCategoriaIdAndAnoAndMes(
            Long usuarioId, Long categoriaId, short ano, short mes);

    List<OrcamentoFechamento> findByUsuarioIdAndAnoAndMes(Long usuarioId, short ano, short mes);

    boolean existsByUsuarioIdAndAnoAndMes(Long usuarioId, short ano, short mes);
}
