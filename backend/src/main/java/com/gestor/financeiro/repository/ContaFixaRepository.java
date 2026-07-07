package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.enums.StatusPagamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaFixaRepository extends JpaRepository<ContaFixa, Long> {
    
    @EntityGraph(attributePaths = {"categoria"})
    List<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    @EntityGraph(attributePaths = {"categoria"})
    Page<ContaFixa> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria"})
    Optional<ContaFixa> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndAtivoTrue(Long usuarioId);

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
}