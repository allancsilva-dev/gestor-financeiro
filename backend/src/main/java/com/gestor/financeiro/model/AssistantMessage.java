package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_messages", uniqueConstraints = @UniqueConstraint(
        name = "ux_assistant_message_idempotency", columnNames = {"usuario_id", "idempotency_key"}))
@Getter @Setter @NoArgsConstructor
public class AssistantMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id", nullable = false)
    private AssistantConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Column(nullable = false, length = 16)
    private String role;
    @Column(nullable = false, length = 2000)
    private String content;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
    @Column(name = "request_hash", length = 64)
    private String requestHash;
    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;
}
