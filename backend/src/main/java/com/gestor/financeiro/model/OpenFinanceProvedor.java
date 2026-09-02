package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Provedor de dados financeiros (agregador, acesso direto ou o fake determinístico de teste).
 *
 * <p>Não guarda endpoint nem segredo: {@link #configRef} nomeia o prefixo de property onde essas
 * coisas vivem. Colocar URL no banco transformaria uma linha de catálogo em destino de requisição
 * controlável por dado, que é como SSRF entra.</p>
 */
@Entity
@Table(name = "open_finance_provedores")
@Getter
@Setter
public class OpenFinanceProvedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "config_ref", length = 60)
    private String configRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Padrão do projeto: carimbo pela aplicação, não pelo DEFAULT do banco — o H2 dos
    // testes é criado a partir da entidade e não herda o default da migration.
    @PrePersist
    void aoCriar() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
