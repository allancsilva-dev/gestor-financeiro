package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoMovimentacao;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "movimentacoes_ativo")
public class MovimentacaoAtivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ativo_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnoreProperties("movimentacoes")
    private Ativo ativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacao tipo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;

    /** CONCILIADA (caixa vinculado) ou EXTERNO/snapshot (ADR-0011, PR-F2-13). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.gestor.financeiro.model.enums.ConciliacaoInvestimento conciliacao =
            com.gestor.financeiro.model.enums.ConciliacaoInvestimento.EXTERNO;

    /** Operacao financeira que liga caixa e posicao (ADR-0009). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operacao_id")
    private OperacaoFinanceira operacao;

    /**
     * Chave de idempotencia derivada do header Idempotency-Key (BACKLOG-0081).
     * Unicidade garantida por indice parcial (V44); nula quando o cliente nao
     * envia o header.
     */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}
