package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.FaturaLancamento;
import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaLancamentoRepository extends JpaRepository<FaturaLancamento, Long> {

    @EntityGraph(attributePaths = {"transacao", "transacao.categoria"})
    List<FaturaLancamento> findByFaturaIdOrderByDataCompraAscIdAsc(Long faturaId);

    /**
     * Soma dos lancamentos por fatura, em lote.
     *
     * A soma e a fonte da verdade do total (ver FaturaService.toResponse e o que
     * pagarFatura valida); o valorTotal persistido so cobre faturas pre-V17 sem
     * lancamentos. A carteira precisa da mesma regra para nao mostrar um valor na
     * lista e outro no detalhe da mesma fatura.
     */
    @Query("SELECT l.fatura.id, COALESCE(SUM(l.valor), 0) FROM FaturaLancamento l "
         + "WHERE l.fatura.id IN :faturaIds GROUP BY l.fatura.id")
    List<Object[]> somarPorFatura(@Param("faturaIds") Collection<Long> faturaIds);

    /**
     * Parcelas agendadas do carrossel da home. Compra parcelada no cartao nao
     * gera linha em `parcelas` desde a V36: ela vive como lancamento COMPRA
     * espalhado pelas faturas seguintes. Traz so o que ainda vence e nao foi
     * pago, mais novo primeiro pela data de vencimento da fatura.
     */
    @EntityGraph(attributePaths = {"transacao", "transacao.categoria", "fatura", "fatura.conta"})
    @Query("""
           SELECT fl FROM FaturaLancamento fl
           WHERE fl.fatura.usuario.id = :usuarioId
             AND fl.tipo = :tipo
             AND fl.totalParcelas > 1
             AND fl.fatura.status <> :paga
             AND fl.fatura.dataVencimento >= :desde
           ORDER BY fl.fatura.dataVencimento ASC, fl.id ASC
           """)
    List<FaturaLancamento> findParcelasAgendadas(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoFaturaLancamento tipo,
            @Param("paga") com.gestor.financeiro.model.enums.FaturaStatus paga,
            @Param("desde") java.time.LocalDate desde);

    // Parte cartao da visao COMPETENCIA (ADR-0010): consumo pela data da compra
    // (COMPRA/AJUSTE/ESTORNO); rollover entre faturas fica fora
    @Query("SELECT COALESCE(SUM(fl.valor), 0) FROM FaturaLancamento fl " +
           "WHERE fl.fatura.usuario.id = :usuarioId " +
           "AND fl.tipo IN :tipos " +
           "AND fl.dataCompra BETWEEN :inicio AND :fim")
    BigDecimal sumConsumoPorDataCompra(
            @Param("usuarioId") Long usuarioId,
            @Param("tipos") List<TipoFaturaLancamento> tipos,
            @Param("inicio") java.time.LocalDate inicio,
            @Param("fim") java.time.LocalDate fim);

    List<FaturaLancamento> findByTransacaoId(Long transacaoId);

    @EntityGraph(attributePaths = {"fatura"})
    List<FaturaLancamento> findByTransacaoIdAndTipoOrderByParcelaNumeroAscIdAsc(
            Long transacaoId, TipoFaturaLancamento tipo);

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
