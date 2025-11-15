package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository // Indica que é um Repository (Spring gerencia automaticamente)
public interface ParcelaRepository extends JpaRepository<Parcela, Long> {
    // JpaRepository<Parcela, Long> → Parcela = entidade, Long = tipo do ID
    
    // Busca todas as parcelas de uma transação específica
    // Spring cria a query automaticamente: SELECT * FROM parcelas WHERE transacao_id = ?
    List<Parcela> findByTransacaoId(Long transacaoId);
}