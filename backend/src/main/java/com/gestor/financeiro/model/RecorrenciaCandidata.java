package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.StatusRecorrenciaCandidata;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Padrão de repetição encontrado no histórico do titular.
 *
 * <p>É sugestão, não compromisso: só vira {@link ContaFixa} quando a pessoa confirma. Detectar e
 * criar sozinho seria o app assumindo dívida no lugar do dono.</p>
 */
@Entity
@Table(name = "recorrencia_candidatas")
@Getter
@Setter
public class RecorrenciaCandidata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Preenchido na confirmação; é o vínculo com a recorrência que nasceu daqui. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_fixa_id")
    private ContaFixa contaFixa;

    @Column(name = "descricao_normalizada", nullable = false, length = 200)
    private String descricaoNormalizada;

    @Column(name = "descricao_exibicao", nullable = false, length = 200)
    private String descricaoExibicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTransacao tipo;

    @Column(name = "valor_medio", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorMedio;

    @Column(name = "dia_tipico", nullable = false)
    private short diaTipico;

    @Column(nullable = false)
    private short ocorrencias;

    @Column(name = "primeira_data", nullable = false)
    private LocalDate primeiraData;

    @Column(name = "ultima_data", nullable = false)
    private LocalDate ultimaData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusRecorrenciaCandidata status = StatusRecorrenciaCandidata.SUGERIDA;

    @Column(name = "detectada_em", nullable = false, updatable = false)
    private Instant detectadaEm;

    @Column(name = "decidida_em")
    private Instant decididaEm;

    @PrePersist
    void prePersist() {
        detectadaEm = Instant.now();
    }
}
