package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Log operacional. Contadores e código de erro; nunca descrição, valor ou id de transação. */
@Entity
@Table(name = "sync_execucoes")
@Getter
@Setter
public class SyncExecucao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_conectada_id")
    private ContaConectada contaConectada;

    /** Terceira camada de reentrância, junto com a job key da fila e a idempotency key do lote. */
    @Column(name = "job_key", nullable = false, length = 180)
    private String jobKey;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "janela_inicio")
    private LocalDate janelaInicio;

    @Column(name = "janela_fim")
    private LocalDate janelaFim;

    @Column(name = "iniciado_em", nullable = false)
    private Instant iniciadoEm;

    @Column(name = "finalizado_em")
    private Instant finalizadoEm;

    @Column(nullable = false, length = 10)
    private String status = "OK";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @Column(name = "registros_recebidos", nullable = false)
    private int registrosRecebidos;

    @Column(name = "registros_novos", nullable = false)
    private int registrosNovos;

    @Column(name = "erro_codigo", length = 60)
    private String erroCodigo;

    @Column(name = "http_status")
    private Short httpStatus;

    @PrePersist
    void aoCriar() {
        if (iniciadoEm == null) iniciadoEm = Instant.now();
    }
}
