package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContaRepository extends JpaRepository<Conta, Long> {
    
    // Busca contas ATIVAS do usuário
    List<Conta> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    
    // Busca TODAS as contas do usuário
    List<Conta> findByUsuarioId(Long usuarioId);

    Optional<Conta> findByIdAndUsuarioId(Long id, Long usuarioId);
}