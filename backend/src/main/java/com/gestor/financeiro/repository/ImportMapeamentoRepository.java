package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.ImportMapeamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportMapeamentoRepository extends JpaRepository<ImportMapeamento, Long> {

    List<ImportMapeamento> findByUsuarioIdOrderByNomeAsc(Long usuarioId);

    Optional<ImportMapeamento> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<ImportMapeamento> findByUsuarioIdAndNome(Long usuarioId, String nome);
}
