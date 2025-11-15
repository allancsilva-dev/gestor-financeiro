package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoConta;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "contas")
public class Conta {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @Column(nullable = false)
    private String nome;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConta tipo;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal limiteTotal = BigDecimal.ZERO;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorGasto = BigDecimal.ZERO;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal saldoAtual = BigDecimal.ZERO;
    
    @Column
    private Integer diaFechamento;
    
    @Column
    private Integer diaVencimento;
    
    @Column
    private Boolean ativo = true;
    
    @Column
    private String cor;
}