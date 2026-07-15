package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.OrigemOperacaoFinanceira;
import com.gestor.financeiro.model.enums.PoliticaOperacao;
import com.gestor.financeiro.model.enums.StatusOperacaoFinanceira;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agrupador imutavel de lancamentos do ledger operacional (ADR-0009).
 * Operacao CONFIRMADA nunca tem conteudo financeiro alterado; correcao gera
 * nova operacao de tipo ESTORNO referenciando esta via estornoDe.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operacoes_financeiras")
public class OperacaoFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoOperacaoFinanceira tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOperacaoFinanceira status = StatusOperacaoFinanceira.CONFIRMADA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoliticaOperacao politica = PoliticaOperacao.CAIXA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemOperacaoFinanceira origem = OrigemOperacaoFinanceira.MANUAL;

    @Column(name = "data_operacao", nullable = false)
    private LocalDateTime dataOperacao;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estorno_de_id")
    private OperacaoFinanceira estornoDe;

    @Column(length = 255)
    private String descricao;

    @PrePersist
    private void aoPersistir() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (dataOperacao == null) {
            dataOperacao = dataCriacao;
        }
    }
}
