package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByUsuarioIdOrderByLidaAscCriadaEmDesc(Long usuarioId, Pageable pageable);

    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndLidaFalse(Long usuarioId);

    @Query("select n.chave from Notificacao n where n.usuario.id = :usuarioId")
    Set<String> findChavesDoUsuario(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("update Notificacao n set n.lida = true where n.usuario.id = :usuarioId and n.lida = false")
    int marcarTodasComoLidas(@Param("usuarioId") Long usuarioId);
}
