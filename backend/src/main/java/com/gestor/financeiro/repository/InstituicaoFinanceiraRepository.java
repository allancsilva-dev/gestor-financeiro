package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.InstituicaoFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InstituicaoFinanceiraRepository extends JpaRepository<InstituicaoFinanceira, Long> {

    Optional<InstituicaoFinanceira> findByCodigoAndAtivaTrue(String codigo);

    /** Resolução por alias: o mesmo banco aparece com nomes diferentes conforme a fonte. */
    @Query("""
            select a.instituicao from InstituicaoAlias a
             where a.alias = :alias and a.instituicao.ativa = true
            """)
    Optional<InstituicaoFinanceira> findByAlias(@Param("alias") String alias);
}
