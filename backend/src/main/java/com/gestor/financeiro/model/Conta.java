package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Configuracao interna de cartao de credito (PR-F2-19, contract V41). Toda
 * linha de contas e um cartao: a divida vive exclusivamente no ledger da conta
 * financeira PASSIVO pareada 1:1 (ADR-0008/0009).
 */
@Data
@Entity
@Table(name = "contas")
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal limiteTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer diaFechamento;

    @Column(nullable = false)
    private Integer diaVencimento;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column
    private String cor;

    @Column(length = 60)
    private String banco;

    /**
     * Quatro ultimos digitos do cartao, para o usuario reconhecer qual e.
     * NUNCA o numero completo (PAN) — ver V42 e o CHECK na coluna.
     */
    @Column(name = "ultimos_digitos", length = 4)
    private String ultimosDigitos;

    @Column(length = 20)
    private String bandeira;

    /**
     * Conta financeira passiva do cartao (PR-F2-06, ADR-0008): 1:1 obrigatorio
     * apos a V41. Pareamento ausente e corrupcao de dados e deve falhar.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_financeira_id", nullable = false, unique = true)
    private Carteira contaFinanceira;
}
