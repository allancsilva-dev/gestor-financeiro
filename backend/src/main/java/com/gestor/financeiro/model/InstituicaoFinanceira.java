package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Instituição canônica: a linha para a qual todos os nomes do mesmo banco convergem.
 *
 * <p>Existe porque a identidade forte da deduplicação comparava texto livre. O código de um arquivo
 * OFX e o de um agregador para o mesmo banco são strings diferentes, e sem convergência o mesmo
 * fato entra duas vezes por rotas diferentes.</p>
 */
@Entity
@Table(name = "instituicoes_financeiras")
@Getter
@Setter
public class InstituicaoFinanceira {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provedor_id")
    private OpenFinanceProvedor provedor;

    @Column(nullable = false, length = 80, unique = true)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 8)
    private String ispb;

    @Column(name = "suporta_contas", nullable = false)
    private boolean suportaContas = true;

    @Column(name = "suporta_cartoes", nullable = false)
    private boolean suportaCartoes = false;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Padrão do projeto: carimbo pela aplicação, não pelo DEFAULT do banco — o H2 dos
    // testes é criado a partir da entidade e não herda o default da migration.
    @PrePersist
    void aoCriar() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
