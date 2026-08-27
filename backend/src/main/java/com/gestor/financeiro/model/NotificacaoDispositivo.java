package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Aparelho autorizado a receber push. O token é credencial de entrega, nunca aparece em log. */
@Entity
@Table(name = "notificacao_dispositivos")
@Getter
@Setter
public class NotificacaoDispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "push_token", nullable = false, length = 200)
    private String pushToken;

    @Column(nullable = false, length = 10)
    private String plataforma;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void prePersist() {
        Instant agora = Instant.now();
        criadoEm = agora;
        atualizadoEm = agora;
    }

    @PreUpdate
    void preUpdate() {
        atualizadoEm = Instant.now();
    }

    /** Nunca imprime o token: log e stack trace não são lugar de credencial de entrega. */
    @Override
    public String toString() {
        return "NotificacaoDispositivo(id=" + id + ", plataforma=" + plataforma + ", ativo=" + ativo + ")";
    }
}
