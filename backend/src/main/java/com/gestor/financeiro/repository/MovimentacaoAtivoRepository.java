package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.MovimentacaoAtivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MovimentacaoAtivoRepository extends JpaRepository<MovimentacaoAtivo, Long> {
    List<MovimentacaoAtivo> findByAtivoIdAndUsuarioIdOrderByDataDesc(Long ativoId, Long usuarioId);

    /** Decomposicao da variacao patrimonial (ADR-0013): soma por tipo no periodo. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(ma.valorTotal), 0) FROM MovimentacaoAtivo ma " +
            "WHERE ma.usuario.id = :usuarioId AND ma.tipo = :tipo " +
            "AND ma.data BETWEEN :inicio AND :fim")
    java.math.BigDecimal sumValorPorTipoNoPeriodo(
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
            @org.springframework.data.repository.query.Param("tipo") com.gestor.financeiro.model.enums.TipoMovimentacao tipo,
            @org.springframework.data.repository.query.Param("inicio") java.time.LocalDate inicio,
            @org.springframework.data.repository.query.Param("fim") java.time.LocalDate fim);
}
