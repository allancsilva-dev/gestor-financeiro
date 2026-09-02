package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Nome alternativo da mesma instituição. Um alias pertence a exatamente uma instituição. */
@Entity
@Table(name = "instituicao_aliases")
@Getter
@Setter
public class InstituicaoAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private InstituicaoFinanceira instituicao;

    @Column(nullable = false, length = 80, unique = true)
    private String alias;
}
