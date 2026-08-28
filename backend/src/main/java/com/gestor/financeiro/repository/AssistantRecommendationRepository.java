package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.AssistantRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssistantRecommendationRepository extends JpaRepository<AssistantRecommendation, Long> {
    Optional<AssistantRecommendation> findByIdAndUsuarioId(Long id, Long usuarioId);
    Optional<AssistantRecommendation> findByUsuarioIdAndRuleCodeAndPeriodStartAndPeriodEnd(
            Long usuarioId, String ruleCode, java.time.LocalDate periodStart, java.time.LocalDate periodEnd);
}
