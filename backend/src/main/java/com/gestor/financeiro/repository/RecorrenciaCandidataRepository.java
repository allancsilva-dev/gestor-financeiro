package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.RecorrenciaCandidata;
import com.gestor.financeiro.model.enums.StatusRecorrenciaCandidata;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecorrenciaCandidataRepository extends JpaRepository<RecorrenciaCandidata, Long> {

    List<RecorrenciaCandidata> findByUsuarioIdAndStatusOrderByUltimaDataDesc(
            Long usuarioId, StatusRecorrenciaCandidata status);

    Optional<RecorrenciaCandidata> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<RecorrenciaCandidata> findByUsuarioIdAndDescricaoNormalizadaAndTipo(
            Long usuarioId, String descricaoNormalizada, TipoTransacao tipo);
}
