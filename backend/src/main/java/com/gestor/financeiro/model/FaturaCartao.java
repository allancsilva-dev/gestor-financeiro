package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.FaturaStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "faturas_cartao", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"conta_id", "mes", "ano"})
})
public class FaturaCartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false)
    private Integer ano;

    @Column(name = "data_fechamento")
    private LocalDate dataFechamento;

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "valor_pago", precision = 10, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FaturaStatus status = FaturaStatus.ABERTA;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
    }
}
