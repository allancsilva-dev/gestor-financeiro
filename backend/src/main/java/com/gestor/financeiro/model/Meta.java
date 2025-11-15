package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "metas")
public class Meta {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @Column(nullable = false)
    private String nome;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal valorTotal;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorReservado = BigDecimal.ZERO;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorMensal;
    
    @Column
    private LocalDate dataInicio;
    
    @Column
    private LocalDate dataPrevista;
    
    @Column
    private LocalDate dataConclusao;
    
    @Column
    private Boolean ativa = true;
    
    @Column
    private String cor;
    
    @Column
    private String icone;
    
    @Column(length = 500)
    private String descricao;
}