package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoAtivo;
import jakarta.persistence.*;
import lombok.Data;
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

    @Column(precision = 18, scale = 2)
    private BigDecimal custoTotal;

    @Version
    private Long version;

    @OneToMany(mappedBy = "ativo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovimentacaoAtivo> movimentacoes = new ArrayList<>();
}
