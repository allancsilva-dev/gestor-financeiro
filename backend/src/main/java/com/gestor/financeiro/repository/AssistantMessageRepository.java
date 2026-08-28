package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.AssistantMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {
    List<AssistantMessage> findByConversationIdAndUsuarioIdOrderByCreatedAt(Long conversationId, Long usuarioId);
    Optional<AssistantMessage> findByUsuarioIdAndIdempotencyKey(Long usuarioId, String idempotencyKey);
}
