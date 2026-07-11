package com.gestor.financeiro.model;

import com.gestor.financeiro.model.enums.TipoFaturaLancamento;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "fatura_lancamentos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"fatura_id", "transacao_id", "parcela_numero"})
})
public class FaturaLancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id", nullable = false)
    private FaturaCartao fatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transacao_id", nullable = true)
    private Transacao transacao;

    // Preenchido apenas em lancamentos de rollover (CREDITO_ANTERIOR/SALDO_DEVEDOR_ANTERIOR):
    // referencia a fatura de origem de onde o credito/divida foi rolado. Ver V25.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_origem_id")
    private FaturaCartao faturaOrigem;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_compra", nullable = false)
    private LocalDate dataCompra;

    @Column(name = "parcela_numero")
    private Integer parcelaNumero;

    @Column(name = "total_parcelas")
    private Integer totalParcelas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoFaturaLancamento tipo = TipoFaturaLancamento.COMPRA;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
    }
}
