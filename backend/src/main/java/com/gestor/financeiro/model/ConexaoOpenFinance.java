package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Vínculo do titular com uma instituição através de um provedor. Sobrevive à revogação, como histórico. */
@Entity
@Table(name = "conexoes_open_finance")
@Getter
@Setter
public class ConexaoOpenFinance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provedor_id", nullable = false)
    private OpenFinanceProvedor provedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instituicao_id")
    private InstituicaoFinanceira instituicao;

    @Column(length = 60)
    private String apelido;

    @Column(nullable = false, length = 16)
    private String status = "PENDENTE";

    @Column(name = "external_connection_id", length = 120)
    private String externalConnectionId;

    @Column(name = "ultima_sync_em")
    private Instant ultimaSyncEm;

    @Column(name = "ultimo_erro_codigo", length = 60)
    private String ultimoErroCodigo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (createdAt == null) createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        updatedAt = Instant.now();
    }
}
