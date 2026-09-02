package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "contas_fixas")
public class ContaFixa {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_id")
    private Carteira carteira;

    // Destino alternativo a carteira: assinatura cobrada no cartao (V67).
    // Exclusoes de Lombok porque @Data percorreria o proxy lazy em toString/equals.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conta conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTransacao tipo = TipoTransacao.SAIDA;

    @Column(name = "execucao_automatica", nullable = false)
    private Boolean execucaoAutomatica = false;
    
    @Column(nullable = false)
    private String nome;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal valorPlanejado;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorReal;
    
    /**
     * Dia do mes da cobranca (1..31). Em frequencia sub-mensal a serie nao sai daqui e
     * sim de dataAncora: o valor e derivado do dia da ancora, so para exibicao e para
     * satisfazer o NOT NULL herdado da V1.
     */
    @Column(nullable = false)
    private Integer diaVencimento;

    /** Periodicidade da cobranca (V72). Default MENSAL preserva o comportamento anterior. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrequenciaRecorrencia frequencia = FrequenciaRecorrencia.MENSAL;

    /**
     * Primeira ocorrencia de recorrencia sub-mensal; fixa o dia da semana e a paridade
     * da quinzena. NULL em MENSAL+, onde a serie sai de diaVencimento.
     */
    @Column(name = "data_ancora")
    private LocalDate dataAncora;
    
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
