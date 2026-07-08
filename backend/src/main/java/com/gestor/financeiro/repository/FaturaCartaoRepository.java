package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.FaturaCartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaCartaoRepository extends JpaRepository<FaturaCartao, Long> {

    Optional<FaturaCartao> findByContaIdAndMesAndAno(Long contaId, Integer mes, Integer ano);

    Optional<FaturaCartao> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<FaturaCartao> findByContaIdAndUsuarioIdOrderByAnoDescMesDesc(Long contaId, Long usuarioId);
}
