package com.gestor.financeiro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historico explicito de pagamentos de fatura (V33, ADR-0009).
 * Cada pagamento parcial/total vira um registro proprio vinculado a operacao
 * financeira que debitou o caixa e reduziu o passivo (fluxo no PR-F2-08).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fatura_pagamentos")
public class FaturaPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id", nullable = false)
    private FaturaCartao fatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_id", nullable = false)
    private Carteira carteira;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operacao_id")
    private OperacaoFinanceira operacao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDateTime dataPagamento;

    @PrePersist
    private void aoPersistir() {
        if (dataPagamento == null) {
            dataPagamento = LocalDateTime.now();
        }
    }
}
