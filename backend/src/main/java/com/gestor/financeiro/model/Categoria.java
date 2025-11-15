package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    private String cor;

    private String icone;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorEsperado = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorGasto = BigDecimal.ZERO;

    private Boolean ativo = true;
}
