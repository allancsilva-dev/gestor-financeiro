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

    /**
     * Ultima ocorrencia ja processada da recorrencia. Serve de piso para a serie: mudar a
     * ancora pode recalcular o proximo vencimento para uma data anterior a uma cobranca
     * ja feita, e o unique (conta_fixa_id, data_vencimento) so pega colisao no MESMO dia
     * — mes ja cobrado com dia diferente passaria e viraria cobranca dupla.
     */
    Optional<ExecucaoRecorrencia> findTopByContaFixaIdAndStatusInOrderByDataVencimentoDesc(
            Long contaFixaId, List<StatusExecucaoRecorrencia> statuses);

    boolean existsByContaFixaIdAndDataVencimentoAndStatusIn(
            Long contaFixaId, LocalDate dataVencimento, List<StatusExecucaoRecorrencia> statuses);

    @EntityGraph(attributePaths = {"contaFixa", "contaFixa.categoria", "contaFixa.carteira"})
    List<ExecucaoRecorrencia> findByUsuarioIdAndStatusAndContaFixaAtivoTrueOrderByDataVencimentoAsc(
            Long usuarioId, StatusExecucaoRecorrencia status);
}
