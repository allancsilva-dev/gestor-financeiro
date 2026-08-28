package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_recommendations", uniqueConstraints = @UniqueConstraint(
        name = "ux_assistant_recommendation_period_rule",
        columnNames = {"usuario_id", "rule_code", "period_start", "period_end"}))
@Getter @Setter @NoArgsConstructor
public class AssistantRecommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;
    @Column(name = "facts_json", nullable = false, columnDefinition = "text")
    private String factsJson;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(name = "sources_json", nullable = false, columnDefinition = "text")
    private String sourcesJson;
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;
    @Column(name = "action_target", nullable = false, length = 180)
    private String actionTarget;
    @Column(nullable = false, length = 1000)
    private String explanation;
    @Column(length = 12)
    private String feedback;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
