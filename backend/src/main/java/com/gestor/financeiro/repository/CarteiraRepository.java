package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.repository.projection.LedgerSaldoProjection;
import com.gestor.financeiro.repository.projection.PassivoFaturaProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarteiraRepository extends JpaRepository<Carteira, Long> {
    
    List<Carteira> findByUsuarioId(Long usuarioId);

    Page<Carteira> findByUsuarioId(Long usuarioId, Pageable pageable);

    Optional<Carteira> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Carteira c WHERE c.id = :id AND c.usuario.id = :usuarioId")
    Optional<Carteira> findByIdAndUsuarioIdForUpdate(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    Optional<Carteira> findByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);

    /**
     * Saldo total de caixa legado: somente contas ATIVO, sem o passivo do
     * cartao (PR-F2-06) e sem COFRE de meta (PR-F2-11) — reservado nunca esteve
     * no saldo disponivel e continua fora (ADR-0012/0013).
     */
    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId "
            + "AND c.natureza = com.gestor.financeiro.model.enums.NaturezaContaFinanceira.ATIVO "
            + "AND c.subtipo <> com.gestor.financeiro.model.enums.SubtipoContaFinanceira.COFRE")
    BigDecimal sumSaldoByUsuarioId(@Param("usuarioId") Long usuarioId);

    /** Listagem legada de /carteiras: oculta cartao (passivo) e cofres de meta. */
    Page<Carteira> findByUsuarioIdAndSubtipoNotIn(Long usuarioId,
            java.util.List<com.gestor.financeiro.model.enums.SubtipoContaFinanceira> subtipos,
            Pageable pageable);

    // --- Metricas oficiais (ADR-0013, PR-F2-15) ---

    /** Disponivel agora: ATIVO com liquidez IMEDIATA (COFRE entra se IMEDIATA). */
    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId "
            + "AND c.natureza = com.gestor.financeiro.model.enums.NaturezaContaFinanceira.ATIVO "
            + "AND c.liquidez = com.gestor.financeiro.model.enums.LiquidezContaFinanceira.IMEDIATA")
    BigDecimal sumDisponivelAgora(@Param("usuarioId") Long usuarioId);

    /** Total ATIVO (qualquer liquidez, inclui COFRE) para patrimonio. */
    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId "
            + "AND c.natureza = com.gestor.financeiro.model.enums.NaturezaContaFinanceira.ATIVO")
    BigDecimal sumAtivoTotal(@Param("usuarioId") Long usuarioId);

    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId "
            + "AND c.subtipo = :subtipo")
    BigDecimal sumSaldoPorSubtipo(@Param("usuarioId") Long usuarioId,
            @Param("subtipo") com.gestor.financeiro.model.enums.SubtipoContaFinanceira subtipo);

    /** Passivo assinado (credito negativo reduz divida) para patrimonio. */
    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId "
            + "AND c.natureza = com.gestor.financeiro.model.enums.NaturezaContaFinanceira.PASSIVO")
    BigDecimal sumPassivoAssinado(@Param("usuarioId") Long usuarioId);

    /** Dividas = soma de max(passivo, 0); credito de cartao nao vira divida. */
    @Query("SELECT COALESCE(SUM(CASE WHEN c.saldo > 0 THEN c.saldo ELSE 0 END), 0) "
            + "FROM Carteira c WHERE c.usuario.id = :usuarioId "
            + "AND c.natureza = com.gestor.financeiro.model.enums.NaturezaContaFinanceira.PASSIVO")
    BigDecimal sumDividas(@Param("usuarioId") Long usuarioId);

    java.util.List<Carteira> findByUsuarioIdAndSubtipo(Long usuarioId,
            com.gestor.financeiro.model.enums.SubtipoContaFinanceira subtipo);

    java.util.List<Carteira> findByUsuarioIdAndNatureza(Long usuarioId,
            com.gestor.financeiro.model.enums.NaturezaContaFinanceira natureza);

    @Query("""
            SELECT c.id AS carteiraId,
                   c.usuario.id AS usuarioId,
                   c.saldo AS saldoMaterializado,
                   COALESCE(SUM(m.valorAssinado), 0) AS saldoLedger
            FROM Carteira c
            LEFT JOIN MovimentoCarteira m ON m.carteira.id = c.id AND m.usuario.id = c.usuario.id
            WHERE c.usuario.id = :usuarioId
            GROUP BY c.id, c.usuario.id, c.saldo
            ORDER BY c.id
            """)
    List<LedgerSaldoProjection> reconciliarSaldosByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("""
            SELECT c.id AS carteiraId,
                   c.usuario.id AS usuarioId,
                   c.saldo AS saldoMaterializado,
                   COALESCE(SUM(m.valorAssinado), 0) AS saldoLedger
            FROM Carteira c
            LEFT JOIN MovimentoCarteira m ON m.carteira.id = c.id AND m.usuario.id = c.usuario.id
            WHERE c.usuario.id = :usuarioId
              AND c.id = :carteiraId
            GROUP BY c.id, c.usuario.id, c.saldo
            """)
    Optional<LedgerSaldoProjection> reconciliarSaldoByUsuarioIdAndCarteiraId(
            @Param("usuarioId") Long usuarioId,
            @Param("carteiraId") Long carteiraId
    );

    @Query(value = """
            SELECT co.id AS "cartaoId",
                   cf.id AS "contaFinanceiraId",
                   cf.saldo AS "saldoPassivo",
                   COALESCE(SUM(CASE
                     WHEN f.id IS NOT NULL AND f.status <> 'PAGA'
                       AND NOT EXISTS (SELECT 1 FROM fatura_lancamentos r WHERE r.fatura_origem_id = f.id)
                     THEN COALESCE(f.valor_total, 0) - COALESCE(f.valor_pago, 0)
                     ELSE 0 END), 0) AS "saldoFaturas"
              FROM contas co
              JOIN carteiras cf ON cf.id = co.conta_financeira_id
              LEFT JOIN faturas_cartao f ON f.conta_id = co.id AND f.usuario_id = co.usuario_id
             WHERE co.usuario_id = :usuarioId
             GROUP BY co.id, cf.id, cf.saldo
             ORDER BY co.id
            """, nativeQuery = true)
    List<PassivoFaturaProjection> reconciliarPassivosFaturasByUsuarioId(
            @Param("usuarioId") Long usuarioId);
}
