package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoCasamentoRegra;
import com.gestor.financeiro.model.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Regra determinística: descrição que casa com o padrão recebe esta categoria. */
@Entity
@Table(name = "regras_categoria")
@Getter
@Setter
public class RegraCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /** Sempre normalizado: minúsculo, sem acento, espaços condensados. */
    @Column(nullable = false, length = 120)
    private String padrao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_casamento", nullable = false, length = 16)
    private TipoCasamentoRegra tipoCasamento = TipoCasamentoRegra.CONTEM;

    /** Nulo vale para entrada e saída. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_transacao", length = 10)
    private TipoTransacao tipoTransacao;

    /** Menor número decide primeiro; empate cai no id, para a ordem ser estável. */
    @Column(nullable = false)
    private short prioridade = 100;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    @PrePersist
    void prePersist() {
        Instant agora = Instant.now();
        criadaEm = agora;
        atualizadaEm = agora;
    }

    @PreUpdate
    void preUpdate() {
        atualizadaEm = Instant.now();
    }
}
