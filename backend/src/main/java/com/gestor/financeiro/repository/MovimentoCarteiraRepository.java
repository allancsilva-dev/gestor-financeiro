package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentoCarteiraRepository extends JpaRepository<MovimentoCarteira, Long> {

    Optional<MovimentoCarteira> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<MovimentoCarteira> findByUsuarioIdAndIdempotencyKey(Long usuarioId, String idempotencyKey);

    java.util.List<MovimentoCarteira> findByOperacaoIdOrderByValorAssinadoAsc(Long operacaoId);

    // Visao CAIXA (ADR-0010): fluxo real de contas ATIVO no periodo, restrito as
    // origens de consumo/renda (transferencia, meta, investimento e backfill fora)
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(CASE WHEN m.valorAssinado > 0 THEN m.valorAssinado ELSE 0 END), 0), " +
            "       COALESCE(SUM(CASE WHEN m.valorAssinado < 0 THEN -m.valorAssinado ELSE 0 END), 0) " +
            "FROM MovimentoCarteira m " +
            "WHERE m.usuario.id = :usuarioId " +
            "AND m.carteira.natureza = com.gestor.financeiro.model.enums.NaturezaContaFinanceira.ATIVO " +
            "AND m.dataMovimento BETWEEN :inicio AND :fim " +
            "AND m.origem IN :origens")
    java.util.List<Object[]> sumVisaoCaixa(
            @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
            @org.springframework.data.repository.query.Param("origens") java.util.List<com.gestor.financeiro.model.enums.OrigemMovimentoCarteira> origens,
            @org.springframework.data.repository.query.Param("inicio") java.time.LocalDateTime inicio,
            @org.springframework.data.repository.query.Param("fim") java.time.LocalDateTime fim);

    List<MovimentoCarteira> findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(Long usuarioId, Long carteiraId);

    boolean existsByCarteiraIdAndOrigemAndReferenciaTipo(
            Long carteiraId,
            OrigemMovimentoCarteira origem,
            String referenciaTipo
    );

    boolean existsByCarteiraId(Long carteiraId);

    @Query("""
            SELECT COALESCE(SUM(m.valorAssinado), 0)
            FROM MovimentoCarteira m
            WHERE m.usuario.id = :usuarioId
              AND m.carteira.id = :carteiraId
            """)
    BigDecimal sumValorAssinadoByUsuarioIdAndCarteiraId(
            @Param("usuarioId") Long usuarioId,
            @Param("carteiraId") Long carteiraId
    );

    Page<MovimentoCarteira> findByUsuarioIdAndCarteiraIdOrderByDataMovimentoDescIdDesc(
            Long usuarioId, Long carteiraId, Pageable pageable
    );
}
