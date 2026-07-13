package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.FaturaCartao;
import com.gestor.financeiro.model.enums.FaturaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaCartaoRepository extends JpaRepository<FaturaCartao, Long> {

    Optional<FaturaCartao> findByContaIdAndMesAndAno(Long contaId, Integer mes, Integer ano);

    Optional<FaturaCartao> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FaturaCartao> findWithLockByIdAndUsuarioId(Long id, Long usuarioId);

    List<FaturaCartao> findByContaIdAndUsuarioIdOrderByAnoDescMesDesc(Long contaId, Long usuarioId);

    List<FaturaCartao> findByUsuarioId(Long usuarioId);

    // Saldo exigivel: pagamento parcial reduz a projecao. Fatura cujo saldo ja foi
    // rolado aparece somente no destino, evitando dupla cobranca.
    @Query("SELECT COALESCE(SUM(CASE WHEN COALESCE(f.valorTotal, 0) > COALESCE(f.valorPago, 0) " +
           "THEN COALESCE(f.valorTotal, 0) - COALESCE(f.valorPago, 0) ELSE 0 END), 0) " +
           "FROM FaturaCartao f WHERE f.usuario.id = :usuarioId AND f.status <> :statusPago " +
           "AND f.dataVencimento BETWEEN :inicio AND :fim " +
           "AND NOT EXISTS (SELECT 1 FROM FaturaLancamento fl WHERE fl.faturaOrigem = f)")
    BigDecimal somarSaldoRestanteNoPeriodo(@Param("usuarioId") Long usuarioId,
                                            @Param("statusPago") FaturaStatus statusPago,
                                            @Param("inicio") LocalDate inicio,
                                            @Param("fim") LocalDate fim);
}
