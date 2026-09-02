package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Conta ou cartão do parceiro vinculado a um destino no ledger.
 *
 * <p>Destino é exclusivo: carteira <b>ou</b> conta de cartão, nunca os dois, nunca nenhum enquanto
 * ativa. Com dois, a sincronização lançaria em ambos; com nenhum, não saberia onde lançar.</p>
 */
@Entity
@Table(name = "contas_conectadas")
@Getter
@Setter
public class ContaConectada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conexao_id", nullable = false)
    private ConexaoOpenFinance conexao;

    @Column(name = "external_account_id", nullable = false, length = 120)
    private String externalAccountId;

    @Column(nullable = false, length = 16)
    private String tipo;

    @Column(length = 8)
    private String mascara;

    @Column(nullable = false, length = 3)
    private String moeda = "BRL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_id")
    private Carteira carteira;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private Conta conta;

    /** Commit automático é exceção (ADR-0021): nasce falso. */
    @Column(name = "auto_commit", nullable = false)
    private boolean autoCommit = false;

    @Column(name = "divergente_desde")
    private Instant divergenteDesde;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "vinculada_em", nullable = false, updatable = false)
    private Instant vinculadaEm;

    @Version
    private Long version;

    @PrePersist
    void aoCriar() {
        if (vinculadaEm == null) vinculadaEm = Instant.now();
    }
}
