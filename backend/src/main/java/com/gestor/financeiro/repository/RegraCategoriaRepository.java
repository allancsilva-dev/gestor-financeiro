package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.RegraCategoria;
import com.gestor.financeiro.model.enums.TipoCasamentoRegra;
import com.gestor.financeiro.model.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegraCategoriaRepository extends JpaRepository<RegraCategoria, Long> {

    /** Ordem estável: prioridade decide, id desempata. */
    @Query("""
            select r from RegraCategoria r
             where r.usuario.id = :usuarioId and r.ativa = true
             order by r.prioridade asc, r.id asc
            """)
    List<RegraCategoria> ativasDoTitular(@Param("usuarioId") Long usuarioId);

    Optional<RegraCategoria> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<RegraCategoria> findByUsuarioIdAndPadraoAndTipoCasamentoAndTipoTransacao(
            Long usuarioId, String padrao, TipoCasamentoRegra tipoCasamento, TipoTransacao tipoTransacao);

    long countByUsuarioIdAndAtivaTrue(Long usuarioId);
}
