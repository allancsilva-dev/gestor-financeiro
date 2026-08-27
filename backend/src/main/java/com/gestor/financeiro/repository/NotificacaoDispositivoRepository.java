package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.NotificacaoDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificacaoDispositivoRepository extends JpaRepository<NotificacaoDispositivo, Long> {

    Optional<NotificacaoDispositivo> findByPushToken(String pushToken);

    List<NotificacaoDispositivo> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
