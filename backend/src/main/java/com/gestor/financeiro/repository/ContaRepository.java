package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Conta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio da configuracao de cartao (PR-F2-19): toda linha de contas e um
 * cartao pareado 1:1 com sua conta financeira PASSIVO.
 */
@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {

    // Cartoes ativos do usuario. O EntityGraph e obrigatorio: contaFinanceira e
    // LAZY e todo caller le o saldo do passivo pareado — sem ele, uma query por
    // cartao (N+1).
    @EntityGraph(attributePaths = "contaFinanceira")
    List<Conta> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    @EntityGraph(attributePaths = "contaFinanceira")
    Page<Conta> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = "contaFinanceira")
    Optional<Conta> findByIdAndUsuarioId(Long id, Long usuarioId);

    // Todos os cartoes do usuario (inclui inativos)
    List<Conta> findByUsuarioId(Long usuarioId);

    Optional<Conta> findByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);

    long countByUsuarioId(Long usuarioId);
}
