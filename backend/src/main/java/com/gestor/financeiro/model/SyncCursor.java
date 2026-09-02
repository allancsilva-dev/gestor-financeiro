package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Onde a próxima janela começa. Só avança depois que o lote correspondente é criado com sucesso. */
@Entity
@Table(name = "sync_cursores")
@Getter
@Setter
public class SyncCursor {
    @Id
    @Column(name = "conta_conectada_id")
    private Long contaConectadaId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_conectada_id")
    private ContaConectada contaConectada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Formato é do parceiro; não impomos tamanho para não falhar no dia em que ele mudar. */
    @Column(name = "cursor_opaco", columnDefinition = "TEXT")
    private String cursorOpaco;

    @Column(name = "ultima_janela_fim")
    private LocalDate ultimaJanelaFim;

    @Column(name = "ultimo_fato_em")
    private Instant ultimoFatoEm;

    @Column(name = "backfill_concluido", nullable = false)
    private boolean backfillConcluido = false;

    @Column(name = "backfill_desde")
    private LocalDate backfillDesde;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    void carimbar() {
        atualizadoEm = Instant.now();
    }
}
