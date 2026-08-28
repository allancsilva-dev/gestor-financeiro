package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_invocations", uniqueConstraints = @UniqueConstraint(
        name = "ux_assistant_invocation_idempotency", columnNames = {"usuario_id", "idempotency_key"}))
@Getter @Setter @NoArgsConstructor
public class AssistantInvocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false) private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id") private AssistantConversation conversation;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, length = 80) private String model;
    @Column(nullable = false, length = 30) private String operation;
    @Column(nullable = false, length = 30) private String result;
    @Column(name = "prompt_version", nullable = false, length = 30) private String promptVersion;
    @Column(name = "schema_version", nullable = false, length = 30) private String schemaVersion;
    @Column(name = "cost_usd", precision = 12, scale = 6) private BigDecimal costUsd;
    @Column(name = "idempotency_key", length = 100) private String idempotencyKey;
    @Column(name = "request_hash", length = 64) private String requestHash;
    @Column(name = "response_json", columnDefinition = "text") private String responseJson;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "expires_at") private LocalDateTime expiresAt;
}
