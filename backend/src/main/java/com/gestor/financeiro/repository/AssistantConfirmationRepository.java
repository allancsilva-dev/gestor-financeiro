package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.AssistantConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssistantConfirmationRepository extends JpaRepository<AssistantConfirmation, Long> {
    Optional<AssistantConfirmation> findByDraftIdAndUsuarioId(Long draftId, Long usuarioId);
    long countByUsuarioId(Long usuarioId);
}
