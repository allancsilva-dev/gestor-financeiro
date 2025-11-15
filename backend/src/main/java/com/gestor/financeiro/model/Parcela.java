package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.StatusPagamento;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "parcelas")
public class Parcela {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "transacao_id", nullable = false)
    private Transacao transacao;
    
    @Column(nullable = false)
    private Integer numeroParcela;
    
    @Column(nullable = false)
    private Integer totalParcelas;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
    
    @Column(nullable = false)
    private LocalDate dataVencimento;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status = StatusPagamento.PENDENTE;
    
    @Column
    private LocalDate dataPagamento;
}