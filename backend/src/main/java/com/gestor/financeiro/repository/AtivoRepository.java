package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Ativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AtivoRepository extends JpaRepository<Ativo, Long> {
    List<Ativo> findByUsuarioId(Long usuarioId);
    Optional<Ativo> findByIdAndUsuarioId(Long id, Long usuarioId);
}
