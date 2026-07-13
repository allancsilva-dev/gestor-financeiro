package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaLancamentoRepository extends JpaRepository<FaturaLancamento, Long> {

    @EntityGraph(attributePaths = {"transacao", "transacao.categoria"})
    List<FaturaLancamento> findByFaturaIdOrderByDataCompraAscIdAsc(Long faturaId);

    List<FaturaLancamento> findByTransacaoId(Long transacaoId);

    Optional<FaturaLancamento> findTopByTransacaoIdAndTipoOrderByParcelaNumeroDescIdDesc(
            Long transacaoId, TipoFaturaLancamento tipo);

    boolean existsByTransacaoIdAndParcelaNumero(Long transacaoId, Integer parcelaNumero);

    @Query("SELECT fl.transacao.id FROM FaturaLancamento fl " +
           "WHERE fl.transacao IS NOT NULL AND fl.transacao.usuario.id = :usuarioId " +
           "AND fl.transacao.ativa = true AND fl.transacao.parcelado = true " +
           "AND fl.transacao.totalParcelas > 1 AND fl.tipo = :tipoCompra " +
           "AND NOT EXISTS (SELECT 1 FROM FaturaLancamento outro " +
           "    WHERE outro.transacao = fl.transacao AND outro.tipo <> :tipoCompra) " +
           "GROUP BY fl.transacao.id, fl.transacao.valorTotal " +
           "HAVING SUM(fl.valor) <> fl.transacao.valorTotal " +
           "ORDER BY fl.transacao.id")
    List<Long> findTransacaoIdsComResiduoArredondamentoSeguroByUsuarioId(
            @Param("usuarioId") Long usuarioId,
            @Param("tipoCompra") TipoFaturaLancamento tipoCompra);

    @Query("SELECT COALESCE(SUM(fl.valor), 0) FROM FaturaLancamento fl " +
           "WHERE fl.transacao.id = :transacaoId AND fl.tipo = :tipo")
    BigDecimal somarValorByTransacaoIdAndTipo(@Param("transacaoId") Long transacaoId,
                                               @Param("tipo") TipoFaturaLancamento tipo);

    // Idempotencia em codigo do rollover (BACKLOG-0054/0059): R1 (credito) e R2 (saldo
    // devedor) sao mutuamente exclusivos para a mesma fatura de origem, entao checar por
    // fatura_origem_id (sem filtrar tipo) ja garante "no maximo um rollover por origem".
    boolean existsByFaturaOrigemId(Long faturaOrigemId);
}
