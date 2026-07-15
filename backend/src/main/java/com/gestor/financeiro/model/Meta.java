package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.StatusMeta;
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

    @Version
    private Long version;
    
    @ManyToOne(fetch = FetchType.LAZY)
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

    // Fonte canônica de estado (ADR-0004); `ativa` é mantida sincronizada para clientes antigos
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusMeta status = StatusMeta.ATIVA;

    @Column
    private String cor;

    @Column
    private String icone;

    @Column(length = 500)
    private String descricao;

    public void concluir(LocalDate data) {
        if (status == StatusMeta.CONCLUIDA) {
            return;
        }
        status = StatusMeta.CONCLUIDA;
        ativa = false;
        dataConclusao = data;
    }

    public void reativar() {
        status = StatusMeta.ATIVA;
        ativa = true;
        dataConclusao = null;
    }

    public void arquivar() {
        status = StatusMeta.ARQUIVADA;
        ativa = false;
    }
}