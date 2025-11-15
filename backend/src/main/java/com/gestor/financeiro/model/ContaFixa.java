package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.StatusPagamento;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "contas_fixas")
public class ContaFixa {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;
    
    @Column(nullable = false)
    private String nome;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal valorPlanejado;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorReal;
    
    @Column(nullable = false)
    private Integer diaVencimento;
    
    @Column
    private LocalDate dataProximoVencimento;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status = StatusPagamento.PENDENTE;
    
    @Column
    private Boolean recorrente = true;
    
    @Column
    private Boolean ativo = true;
    
    @Column(length = 500)
    private String observacoes;
}