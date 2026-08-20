package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoNotificacao;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Notificacao in-app (V42). Nao ha push: o sino da home le esta tabela.
 *
 * A geracao e idempotente pela `chave`, uma identidade natural do evento
 * (ex.: "FATURA_VENCENDO:42"). Reprocessar o mesmo evento nao cria duplicata,
 * o que permite sincronizar a cada abertura da home sem inflar a caixa.
 */
@Data
@Entity
@Table(name = "notificacoes")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 400)
    private String mensagem;

    /** Destino de navegacao no padrao do PR-F3-04; o cliente nunca inventa link. */
    @Column(length = 40)
    private String destino;

    @Column(name = "destino_id")
    private Long destinoId;

    @Column(nullable = false, length = 120)
    private String chave;

    @Column(nullable = false)
    private Boolean lida = false;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();
}
