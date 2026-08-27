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

    /**
     * Conta financeira COFRE da meta (ADR-0012, PR-F2-11): destino real da
     * reserva. Invariante: valorReservado == saldo do cofre; valorReservado
     * passa a ser derivado no contract (PR-F2-19).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cofre_id", unique = true)
    private Carteira cofre;

    /** Exatamente uma modalidade por meta (ADR-0012, PR-F2-12). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.gestor.financeiro.model.enums.ModalidadeMeta modalidade =
            com.gestor.financeiro.model.enums.ModalidadeMeta.COFRE_REAL;

    /** Conta de caixa da alocacao virtual (sem lancamento no ledger). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_alocada_id")
    private Carteira carteiraAlocada;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal valorMensal;

    /**
     * Aporte automatico e opt-in: preencher valor mensal e planejamento, autorizar o app a mover
     * dinheiro todo mes e outra coisa.
     */
    @Column(name = "aporte_automatico", nullable = false)
    private Boolean aporteAutomatico = false;

    /** Dia do mes do aporte; ate 28 para existir em todo mes. */
    @Column(name = "aporte_dia")
    private Short aporteDia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aporte_carteira_id")
    private Carteira aporteCarteira;

    /** Ultima competencia ja aportada (yyyy-MM); trava o aporte nas duas modalidades. */
    @Column(name = "aporte_ultima_competencia", length = 7)
    private String aporteUltimaCompetencia;
    
    @Column
    private LocalDate dataInicio;
    
    @Column
    private LocalDate dataPrevista;
    
    @Column
    private LocalDate dataConclusao;

    @Column(nullable = false)
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

    /** Mantém status, flag legada e data de conclusão como uma única transição atômica. */
    public void recalcularEstado(LocalDate hoje) {
        if (status == StatusMeta.ARQUIVADA) {
            return;
        }

        BigDecimal reservado = valorReservado == null ? BigDecimal.ZERO : valorReservado;
        if (reservado.compareTo(valorTotal) >= 0) {
            concluir(hoje);
        } else {
            reativar();
        }
    }
}
