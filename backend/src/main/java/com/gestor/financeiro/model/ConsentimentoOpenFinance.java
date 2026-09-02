package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Append-only: revogar muda status, nunca apaga linha. A linha é a prova de conformidade (ADR-0020). */
@Entity
@Table(name = "consentimentos_open_finance")
@Getter
@Setter
public class ConsentimentoOpenFinance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conexao_id", nullable = false)
    private ConexaoOpenFinance conexao;

    @Column(name = "external_consent_id", length = 120)
    private String externalConsentId;

    @Column(nullable = false, length = 300)
    private String escopos;

    @Column(nullable = false, length = 12)
    private String status = "AGUARDANDO";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "concedido_em")
    private Instant concedidoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renovado_de_id")
    private ConsentimentoOpenFinance renovadoDe;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "revogado_por", length = 12)
    private String revogadoPor;

    @Column(name = "politica_versao", length = 20)
    private String politicaVersao;

    @Column(name = "evidencia_hash", length = 64)
    private String evidenciaHash;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) criadoEm = Instant.now();
    }
}
