package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaFixaRepository extends JpaRepository<ContaFixa, Long> {
    
    @EntityGraph(attributePaths = {"categoria", "carteira", "conta"})
    List<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // Todas as contas fixas, inclusive inativas (exportação LGPD)
    @EntityGraph(attributePaths = {"categoria", "carteira", "conta"})
    List<ContaFixa> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"categoria", "carteira", "conta"})
    Page<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "carteira", "conta"})
    Optional<ContaFixa> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContaFixa c LEFT JOIN FETCH c.categoria LEFT JOIN FETCH c.carteira LEFT JOIN FETCH c.conta WHERE c.id = :id AND c.usuario.id = :usuarioId")
    Optional<ContaFixa> findByIdAndUsuarioIdForUpdate(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContaFixa c LEFT JOIN FETCH c.categoria LEFT JOIN FETCH c.carteira LEFT JOIN FETCH c.conta LEFT JOIN FETCH c.usuario WHERE c.id = :id")
    Optional<ContaFixa> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT c.id FROM ContaFixa c WHERE c.ativo = true AND c.execucaoAutomatica = true AND c.dataProximoVencimento <= :data ORDER BY c.dataProximoVencimento")
    List<Long> findIdsAutomaticasVencidas(@Param("data") LocalDate data);

    long countByUsuarioIdAndAtivoTrue(Long usuarioId);

    // Assinatura nao pode continuar sendo cobrada num cartao que o titular removeu.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ContaFixa c SET c.ativo = false WHERE c.conta.id = :contaId AND c.ativo = true")
    int desativarPorConta(@Param("contaId") Long contaId);

    @Modifying
    @Query("UPDATE ContaFixa c SET c.status = :novoStatus, c.valorReal = null WHERE c.status = :status AND c.dataProximoVencimento < :data")
    int resetarContasPagasVencidas(@Param("status") StatusPagamento status,
                                    @Param("novoStatus") StatusPagamento novoStatus,
                                    @Param("data") LocalDate data);

    @Modifying
    @Query("UPDATE ContaFixa c SET c.status = :novoStatus WHERE c.status = :status AND c.dataProximoVencimento < :data")
    int atualizarStatusContasAtrasadas(@Param("status") StatusPagamento status,
                                        @Param("novoStatus") StatusPagamento novoStatus,
                                        @Param("data") LocalDate data);

    // Projecao: soma planejada das contas fixas ativas vencendo no periodo, excluindo pago/cancelado.
    @Query("SELECT COALESCE(SUM(cf.valorPlanejado), 0) FROM ContaFixa cf " +
           "WHERE cf.usuario.id = :usuarioId AND cf.ativo = true " +
           "AND cf.dataProximoVencimento BETWEEN :inicio AND :fim " +
           "AND cf.status <> :pago AND cf.status <> :cancelado")
    BigDecimal somarPlanejadoNoPeriodo(@Param("usuarioId") Long usuarioId,
                                        @Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim,
                                        @Param("pago") StatusPagamento pago,
                                        @Param("cancelado") StatusPagamento cancelado);

    // Compromissos (PR-F3-01): saidas fixas ativas nao pagas vencendo ate o
    // horizonte; vencida nao paga continua prevista (sem limite inferior).
    @Query("SELECT c FROM ContaFixa c WHERE c.usuario.id = :usuarioId AND c.ativo = true " +
           "AND c.tipo = :tipo AND c.status <> :pago AND c.status <> :cancelado " +
           "AND c.dataProximoVencimento IS NOT NULL AND c.dataProximoVencimento <= :ate " +
           "ORDER BY c.dataProximoVencimento, c.id")
    List<ContaFixa> findPrevistasAteHorizonte(@Param("usuarioId") Long usuarioId,
                                              @Param("tipo") TipoTransacao tipo,
                                              @Param("pago") StatusPagamento pago,
                                              @Param("cancelado") StatusPagamento cancelado,
                                              @Param("ate") LocalDate ate);
}
