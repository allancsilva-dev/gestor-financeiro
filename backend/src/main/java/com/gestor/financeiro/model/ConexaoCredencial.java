package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Segredo de terceiro, cifrado. Tabela separada da conexão de propósito: é a primeira coisa que a
 * revogação apaga, e a rotação de chave reescreve só isto, sem tocar histórico.
 */
@Entity
@Table(name = "conexao_credenciais")
@Getter
@Setter
public class ConexaoCredencial {
    @Id
    @Column(name = "conexao_id")
    private Long conexaoId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conexao_id")
    private ConexaoOpenFinance conexao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "access_token_cifrado", columnDefinition = "TEXT")
    private String accessTokenCifrado;

    @Column(name = "refresh_token_cifrado", columnDefinition = "TEXT")
    private String refreshTokenCifrado;

    @Column(name = "token_expira_em")
    private Instant tokenExpiraEm;

    @Column(name = "key_version", nullable = false, length = 10)
    private String keyVersion = "v1";

    @Column(name = "token_hmac", length = 64)
    private String tokenHmac;

    @Column(name = "rotacionado_em")
    private Instant rotacionadoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void aoCriar() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
