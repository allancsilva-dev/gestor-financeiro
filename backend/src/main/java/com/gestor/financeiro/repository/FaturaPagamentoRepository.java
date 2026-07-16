package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.FaturaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaturaPagamentoRepository extends JpaRepository<FaturaPagamento, Long> {

    List<FaturaPagamento> findByFaturaIdOrderByDataPagamentoAsc(Long faturaId);

    List<FaturaPagamento> findByUsuarioId(Long usuarioId);
}
