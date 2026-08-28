package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity @Table(name = "assistant_channel_events")
@Getter @Setter @NoArgsConstructor
public class AssistantChannelEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(nullable = false, length = 20) private String channel;
    @Column(name = "external_id", nullable = false, unique = true, length = 180) private String externalId;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Column(name = "payload_ciphertext", nullable = false, columnDefinition = "text") private String payloadCiphertext;
    @Column(name = "payload_key_version", nullable = false, length = 30) private String payloadKeyVersion;
    @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt;
    @Column(name = "processed_at") private LocalDateTime processedAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
}
