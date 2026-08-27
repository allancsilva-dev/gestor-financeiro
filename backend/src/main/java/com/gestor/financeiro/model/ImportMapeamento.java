package com.gestor.financeiro.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Perfil de mapeamento de colunas de um arquivo, salvo pelo titular para reusar no próximo envio. */
@Entity
@Table(name = "import_mapeamentos")
@Getter
@Setter
public class ImportMapeamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(length = 80)
    private String instituicao;

    /** Nulo deixa a detecção decidir. */
    @Column(length = 1)
    private String delimitador;

    /** JSON campo canônico -> nome da coluna no arquivo; jsonb no PostgreSQL (V56). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String colunas;

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
}
