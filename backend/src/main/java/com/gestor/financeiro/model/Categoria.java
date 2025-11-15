package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "categorias")
public class Categoria {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @Column(nullable = false)
    private String nome;
    
    @Column
    private String cor;
    
    @Column
    private String icone;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorEsperado = BigDecimal.ZERO;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorGasto = BigDecimal.ZERO;
    
    @Column
    private Boolean ativo = true;
}