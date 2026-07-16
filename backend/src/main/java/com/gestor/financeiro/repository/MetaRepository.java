package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.enums.StatusMeta;
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

    // Reservado virtual total do usuario (ADR-0013, PR-F2-15)
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(m.valorReservado), 0) FROM Meta m " +
            "WHERE m.usuario.id = :usuarioId " +
            "AND m.modalidade = com.gestor.financeiro.model.enums.ModalidadeMeta.RESERVA_VIRTUAL " +
            "AND m.status <> com.gestor.financeiro.model.enums.StatusMeta.ARQUIVADA")
    java.math.BigDecimal sumReservaVirtual(
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);

    java.util.List<Meta> findByUsuarioIdAndModalidadeAndStatusNot(Long usuarioId,
            com.gestor.financeiro.model.enums.ModalidadeMeta modalidade,
            StatusMeta status);

    // Soma das alocacoes virtuais de outras metas sobre a mesma conta (PR-F2-12)
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(m.valorReservado), 0) FROM Meta m " +
            "WHERE m.usuario.id = :usuarioId " +
            "AND m.carteiraAlocada.id = :carteiraId " +
            "AND m.id <> :metaId " +
            "AND m.modalidade = com.gestor.financeiro.model.enums.ModalidadeMeta.RESERVA_VIRTUAL " +
            "AND m.status <> com.gestor.financeiro.model.enums.StatusMeta.ARQUIVADA")
    java.math.BigDecimal somarAlocacaoVirtualNaCarteira(
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
            @org.springframework.data.repository.query.Param("carteiraId") Long carteiraId,
            @org.springframework.data.repository.query.Param("metaId") Long metaId);

    // Todas as metas, inclusive inativas (exportação LGPD)
    List<Meta> findByUsuarioId(Long usuarioId);

    // Busca metas ativas com paginação.
    Page<Meta> findByUsuarioIdAndAtivaTrue(Long usuarioId, Pageable pageable);

    // Listagem por status canônico (ADR-0004)
    Page<Meta> findByUsuarioIdAndStatus(Long usuarioId, StatusMeta status, Pageable pageable);

    Optional<Meta> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndAtivaTrue(Long usuarioId);
}
