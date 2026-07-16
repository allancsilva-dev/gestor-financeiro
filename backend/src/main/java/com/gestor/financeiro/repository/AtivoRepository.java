package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Ativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AtivoRepository extends JpaRepository<Ativo, Long> {
    List<Ativo> findByUsuarioId(Long usuarioId);
    Optional<Ativo> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Investido (ADR-0013): posicoes pela ultima cotacao valida (datada). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(a.quantidade * a.valorAtual), 0) FROM Ativo a " +
            "WHERE a.usuario.id = :usuarioId AND a.valorAtual IS NOT NULL " +
            "AND a.cotacaoEm IS NOT NULL AND a.quantidade > 0")
    java.math.BigDecimal sumValorMercadoCotado(
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);
}
