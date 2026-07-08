package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "anexos")
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private Long tamanho;

    @Column(nullable = false)
    private String caminho;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id", nullable = false)
    private Transacao transacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime dataUpload;
}
