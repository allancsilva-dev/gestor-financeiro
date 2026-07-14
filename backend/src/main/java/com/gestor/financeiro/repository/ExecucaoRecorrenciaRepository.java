package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ExecucaoRecorrencia;
import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExecucaoRecorrenciaRepository extends JpaRepository<ExecucaoRecorrencia, Long> {
    Optional<ExecucaoRecorrencia> findByContaFixaIdAndDataVencimento(Long contaFixaId, LocalDate dataVencimento);

    boolean existsByContaFixaIdAndDataVencimentoAndStatusIn(
            Long contaFixaId, LocalDate dataVencimento, List<StatusExecucaoRecorrencia> statuses);

    @EntityGraph(attributePaths = {"contaFixa", "contaFixa.categoria", "contaFixa.carteira"})
    List<ExecucaoRecorrencia> findByUsuarioIdAndStatusAndContaFixaAtivoTrueOrderByDataVencimentoAsc(
            Long usuarioId, StatusExecucaoRecorrencia status);
}
