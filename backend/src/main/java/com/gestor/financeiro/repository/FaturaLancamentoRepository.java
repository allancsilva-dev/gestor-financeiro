package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.FaturaLancamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaturaLancamentoRepository extends JpaRepository<FaturaLancamento, Long> {

    @EntityGraph(attributePaths = {"transacao", "transacao.categoria"})
    List<FaturaLancamento> findByFaturaIdOrderByDataCompraAscIdAsc(Long faturaId);

    List<FaturaLancamento> findByTransacaoId(Long transacaoId);

    boolean existsByTransacaoIdAndParcelaNumero(Long transacaoId, Integer parcelaNumero);

    // Idempotencia em codigo do rollover (BACKLOG-0054/0059): R1 (credito) e R2 (saldo
    // devedor) sao mutuamente exclusivos para a mesma fatura de origem, entao checar por
    // fatura_origem_id (sem filtrar tipo) ja garante "no maximo um rollover por origem".
    boolean existsByFaturaOrigemId(Long faturaOrigemId);
}
