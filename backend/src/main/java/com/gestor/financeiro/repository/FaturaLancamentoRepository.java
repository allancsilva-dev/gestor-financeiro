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
}
