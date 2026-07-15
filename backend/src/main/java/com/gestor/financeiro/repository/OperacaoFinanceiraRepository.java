package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.OperacaoFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperacaoFinanceiraRepository extends JpaRepository<OperacaoFinanceira, Long> {

    Optional<OperacaoFinanceira> findByUsuarioIdAndIdempotencyKey(Long usuarioId, String idempotencyKey);
}
