package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoAtivo;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "ativos")
public class Ativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAtivo tipo;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantidade;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorAtual;

    /** Instante da ultima cotacao manual (ADR-0011); NULL = desatualizada. */
    @Column(name = "cotacao_em")
    private java.time.LocalDateTime cotacaoEm;

    /** Liquidez declarada da posicao (ADR-0011/0013). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private com.gestor.financeiro.model.enums.LiquidezContaFinanceira liquidez =
            com.gestor.financeiro.model.enums.LiquidezContaFinanceira.IMEDIATA;

    /** Conta CUSTODIA que agrupa a posicao (container sem saldo, ADR-0011). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custodia_id")
    private Carteira custodia;

    @Column(precision = 18, scale = 2)
    private BigDecimal custoTotal;

    @Version
    private Long version;

    // Lado inverso do unico ciclo bidirecional de investimentos (BACKLOG-0084):
    // sem as exclusoes, toString/equals/hashCode recursam entre Ativo e
    // MovimentacaoAtivo e ainda forcam o lazy load da colecao.
    @OneToMany(mappedBy = "ativo", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnoreProperties("ativo")
    private List<MovimentacaoAtivo> movimentacoes = new ArrayList<>();
}
