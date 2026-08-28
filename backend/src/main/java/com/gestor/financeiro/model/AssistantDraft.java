package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.AssistantDraftStatus;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assistant_drafts")
@Getter @Setter @NoArgsConstructor
public class AssistantDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "conversation_id")
    private AssistantConversation conversation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AssistantDraftStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private TipoTransacao tipo;
    @Column(precision = 15, scale = 2)
    private BigDecimal valor;
    @Column(length = 500)
    private String descricao;
    private LocalDate data;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "carteira_id")
    private Carteira carteira;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "categoria_id")
    private Categoria categoria;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 80)
    private String model;
    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;
    @Column(name = "schema_version", nullable = false, length = 30)
    private String schemaVersion;
    @Column(name = "question_count", nullable = false)
    private short questionCount;
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
