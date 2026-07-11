package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private int failedAttempts = 0;

    @Column
    private LocalDateTime lockedUntil;

    @Column(name = "onboarding_completo", nullable = false)
    private boolean onboardingCompleto = false;

    // LGPD: versão da política de privacidade aceita no cadastro e quando.
    // Nulo em contas anteriores ao registro de consentimento.
    @Column(name = "politica_versao", length = 20)
    private String politicaVersao;

    @Column(name = "consentimento_em")
    private LocalDateTime consentimentoEm;
}
