package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Table(name = "assistant_whatsapp_links", uniqueConstraints = @UniqueConstraint(
        name = "ux_assistant_whatsapp_link_idempotency", columnNames = {"usuario_id", "idempotency_key"}))
@Getter @Setter @NoArgsConstructor
public class AssistantWhatsappLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false) private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id") private AssistantConversation conversation;
    @Column(name = "wa_ciphertext", columnDefinition = "text") private String waCiphertext;
    @Column(name = "wa_key_version", length = 30) private String waKeyVersion;
    @Column(name = "wa_hmac", length = 64) private String waHmac;
    @Column(name = "code_hash", nullable = false, length = 64) private String codeHash;
    @Column(name = "code_ciphertext", columnDefinition = "text") private String codeCiphertext;
    @Column(name = "idempotency_key", length = 100) private String idempotencyKey;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
