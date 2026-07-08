package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.MovimentoMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimentoMetaRepository extends JpaRepository<MovimentoMeta, Long> {

    List<MovimentoMeta> findByUsuarioIdAndMetaIdOrderByCreatedAtDesc(Long usuarioId, Long metaId);

    Page<MovimentoMeta> findByUsuarioIdAndMetaIdOrderByCreatedAtDesc(Long usuarioId, Long metaId, Pageable pageable);
}
