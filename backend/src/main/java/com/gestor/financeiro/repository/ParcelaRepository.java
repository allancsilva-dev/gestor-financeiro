package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Parcela;
import com.gestor.financeiro.model.enums.StatusPagamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {
    
    @EntityGraph(attributePaths = {"transacao"})
    List<Parcela> findByTransacaoId(Long transacaoId);

    @EntityGraph(attributePaths = {"transacao"})
    List<Parcela> findByTransacaoIdAndTransacaoUsuarioId(Long transacaoId, Long usuarioId);

    @EntityGraph(attributePaths = {"transacao"})
    Page<Parcela> findByTransacaoIdAndTransacaoUsuarioId(Long transacaoId, Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"transacao"})
    Optional<Parcela> findByIdAndTransacaoUsuarioId(Long id, Long usuarioId);

    @Modifying
    @Query("UPDATE Parcela p SET p.status = :novoStatus WHERE p.status = :status AND p.dataVencimento < :data")
    int atualizarStatusParcelasAtrasadas(@Param("status") StatusPagamento status,
                                          @Param("novoStatus") StatusPagamento novoStatus,
                                          @Param("data") LocalDate data);

    @EntityGraph(attributePaths = {"transacao"})
    @Query("SELECT p FROM Parcela p WHERE p.transacao.usuario.id = :usuarioId AND p.dataVencimento >= :inicio AND p.status <> :statusExcluido")
    List<Parcela> findFuturasByUsuarioId(@Param("usuarioId") Long usuarioId,
                                          @Param("inicio") LocalDate inicio,
                                          @Param("statusExcluido") StatusPagamento statusExcluido);

    // Projecao: soma das parcelas vencendo no periodo, excluindo status informado (PAGO).
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM Parcela p " +
           "WHERE p.transacao.usuario.id = :usuarioId AND p.status <> :statusExcluido " +
           "AND p.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarValorNoPeriodo(@Param("usuarioId") Long usuarioId,
                                    @Param("inicio") LocalDate inicio,
                                    @Param("fim") LocalDate fim,
                                    @Param("statusExcluido") StatusPagamento statusExcluido);
}