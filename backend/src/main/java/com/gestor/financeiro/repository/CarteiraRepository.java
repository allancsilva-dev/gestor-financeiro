package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Carteira;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CarteiraRepository extends JpaRepository<Carteira, Long> {
    
    List<Carteira> findByUsuarioId(Long usuarioId);

    Page<Carteira> findByUsuarioId(Long usuarioId, Pageable pageable);

    Optional<Carteira> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<Carteira> findByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);

    @Query("SELECT COALESCE(SUM(c.saldo), 0) FROM Carteira c WHERE c.usuario.id = :usuarioId")
    BigDecimal sumSaldoByUsuarioId(@Param("usuarioId") Long usuarioId);
}