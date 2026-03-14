package com.gestor.financeiro.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Data;
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
    
    @OneToMany(mappedBy = "transacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("transacao") // ✅ LINHA ADICIONADA
    private List<Parcela> parcelas = new ArrayList<>();
    
    @Column
    private String observacoes;
    
    @Column
    private Boolean recorrente = false;
}