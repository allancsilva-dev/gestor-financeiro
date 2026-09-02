package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Saldo publicado pela instituição.
 *
 * <p>Guarda os dois saldos: a conciliação usa o <b>contábil</b> (ADR-0021), e a diferença para o
 * disponível é o diagnóstico de "há pendente lá que aqui ainda não existe". Conciliar contra o
 * disponível produziria divergência permanente, já que só fato efetivado é ingerido.</p>
 */
@Entity
@Table(name = "saldos_declarados_instituicao")
@Getter
@Setter
public class SaldoDeclaradoInstituicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_conectada_id", nullable = false)
    private ContaConectada contaConectada;

    @Column(name = "referencia_em", nullable = false)
    private Instant referenciaEm;

    @Column(name = "saldo_contabil", precision = 19, scale = 2)
    private BigDecimal saldoContabil;

    @Column(name = "saldo_disponivel", precision = 19, scale = 2)
    private BigDecimal saldoDisponivel;

    @Column(name = "limite_cartao", precision = 19, scale = 2)
    private BigDecimal limiteCartao;

    @Column(name = "capturado_em", nullable = false)
    private Instant capturadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_execucao_id")
    private SyncExecucao syncExecucao;

    @PrePersist
    void aoCriar() {
        if (capturadoEm == null) capturadoEm = Instant.now();
    }
}
