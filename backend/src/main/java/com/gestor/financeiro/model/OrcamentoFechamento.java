package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.PoliticaRollover;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Memória do mês de orçamento por categoria.
 *
 * <p>Guarda a conta inteira — base, o que veio de trás, gasto, resultado e o que passa adiante —
 * junto da política e da versão da regra que valiam no fechamento. É isso que permite mudar a
 * política depois sem reescrever a história (ADR-0010/ADR-0014) e explicar ao usuário de onde saiu
 * cada centavo carregado.</p>
 */
@Entity
@Table(name = "orcamento_fechamentos")
@Getter
@Setter
public class OrcamentoFechamento {

    /** Sobe quando a fórmula do fechamento mudar; registro antigo mantém a versão que o gerou. */
    public static final short REGRA_VERSAO_ATUAL = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private short mes;

    @Column(nullable = false)
    private short ano;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal base;

    @Column(name = "carry_in", nullable = false, precision = 12, scale = 2)
    private BigDecimal carryIn;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gasto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal resultado;

    @Column(name = "carry_out", nullable = false, precision = 12, scale = 2)
    private BigDecimal carryOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PoliticaRollover politica;

    @Column(name = "regra_versao", nullable = false)
    private short regraVersao = REGRA_VERSAO_ATUAL;

    @Column(name = "fechado_em", nullable = false, updatable = false)
    private Instant fechadoEm;

    @PrePersist
    void prePersist() {
        fechadoEm = Instant.now();
    }
}
