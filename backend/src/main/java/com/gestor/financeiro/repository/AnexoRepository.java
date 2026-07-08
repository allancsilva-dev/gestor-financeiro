package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Anexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    List<Anexo> findByTransacaoIdAndUsuarioId(Long transacaoId, Long usuarioId);

    java.util.Optional<Anexo> findByIdAndUsuarioId(Long id, Long usuarioId);
}
