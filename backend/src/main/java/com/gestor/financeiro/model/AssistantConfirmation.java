package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_confirmations", uniqueConstraints = @UniqueConstraint(columnNames = "draft_id"))
@Getter @Setter @NoArgsConstructor
public class AssistantConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "draft_id", nullable = false, updatable = false)
    private Long draftId;
    @Column(name = "draft_version", nullable = false, updatable = false)
    private Long draftVersion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "operacao_id", nullable = false)
    private OperacaoFinanceira operacao;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transacao_id", nullable = false)
    private Transacao transacao;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 80)
    private String model;
    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;
    @Column(name = "schema_version", nullable = false, length = 30)
    private String schemaVersion;
    @Column(name = "corrections_json", nullable = false, columnDefinition = "text")
    private String correctionsJson;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
