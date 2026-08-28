package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.AssistantConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, Long> {
    Optional<AssistantConversation> findByIdAndUsuarioId(Long id, Long usuarioId);
}
