package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.StatusExecucaoRecorrencia;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "execucoes_recorrencia", uniqueConstraints =
        @UniqueConstraint(name = "ux_execucao_recorrencia_vencimento", columnNames = {"conta_fixa_id", "data_vencimento"}))
public class ExecucaoRecorrencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_fixa_id", nullable = false)
    private ContaFixa contaFixa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusExecucaoRecorrencia status;

    @Column(name = "tentado_em", nullable = false)
    private LocalDateTime tentadoEm;

    @Column(name = "mensagem_falha", length = 500)
    private String mensagemFalha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id")
    private Transacao transacao;
}
