package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.enums.TipoConta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {
    
    // Busca contas ATIVAS do usuário
    List<Conta> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    // Busca contas ativas com paginação.
    Page<Conta> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = "contaFinanceira")
    Page<Conta> findByUsuarioIdAndTipoAndAtivoTrue(Long usuarioId, TipoConta tipo, Pageable pageable);

    @EntityGraph(attributePaths = "contaFinanceira")
    Optional<Conta> findByIdAndUsuarioIdAndTipo(Long id, Long usuarioId, TipoConta tipo);
    
    // Busca TODAS as contas do usuário
    List<Conta> findByUsuarioId(Long usuarioId);

    Optional<Conta> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<Conta> findByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);

    long countByUsuarioId(Long usuarioId);
}
