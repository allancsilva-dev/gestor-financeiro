package com.gestor.financeiro.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestor.financeiro.model.enums.EstadoConciliacaoTransacao;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "transacoes")
public class Transacao {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private Conta conta;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_fixa_id")
    private ContaFixa contaFixa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_id")
    private Carteira carteira;

    @Column(nullable = false)
    private Boolean ativa = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conciliacao", nullable = false, length = 30)
    private EstadoConciliacaoTransacao estadoConciliacao = EstadoConciliacaoTransacao.CONCILIADA;

    @Column(nullable = false)
    private String descricao;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;
    
    @Column(nullable = false)
    private LocalDate data;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status = StatusPagamento.PENDENTE;
    
    @Column
    private Boolean parcelado = false;
    
    @Column
    private Integer totalParcelas;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorParcela;
    
    // @JsonIgnoreProperties cobria so o Jackson; toString/equals/hashCode
    // continuavam recursando com Parcela (BACKLOG-0084).
    @OneToMany(mappedBy = "transacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("transacao")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Parcela> parcelas = new ArrayList<>();
    
    @Column
    private String observacoes;
    
    @Column
    private Boolean recorrente = false;
}