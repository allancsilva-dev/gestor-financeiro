package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.repository.projection.LedgerSaldoProjection;
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

    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId")
    BigDecimal sumSaldoByUsuarioId(@Param("usuarioId") Long usuarioId);

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
}
