package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "orcamentos_categorias", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"orcamento_id", "categoria_id"})
})
public class OrcamentoCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private OrcamentoMensal orcamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "valor_limite", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorLimite = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean ativo = true;
}
