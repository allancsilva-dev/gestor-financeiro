package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.EstadoConciliacaoConta;
import com.gestor.financeiro.model.enums.LiquidezContaFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.OrigemDadosConta;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "carteiras")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Carteira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(length = 100)
    private String banco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NaturezaContaFinanceira natureza = NaturezaContaFinanceira.ATIVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubtipoContaFinanceira subtipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LiquidezContaFinanceira liquidez = LiquidezContaFinanceira.IMEDIATA;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_dados", nullable = false)
    private OrigemDadosConta origemDados = OrigemDadosConta.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conciliacao", nullable = false)
    private EstadoConciliacaoConta estadoConciliacao = EstadoConciliacaoConta.CONCILIADA;

    @Column(nullable = false, length = 3)
    private String moeda = "BRL";

    /**
     * Conta padrão do titular. No máximo uma por usuário, garantida por índice único parcial
     * (`ux_carteiras_principal_usuario`, V66) — a unicidade é do banco, não da aplicação.
     * Quem muda isto é {@code CarteiraService.definirPrincipal}, que desmarca a atual antes de
     * marcar a nova na mesma transação; escrever direto no setter fura o índice.
     */
    @Column(nullable = false)
    private boolean principal = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @PrePersist
    @PreUpdate
    private void sincronizarClassificacao() {
        if (subtipo != null) {
            natureza = subtipo.naturezaPadrao();
        }
    }
}
