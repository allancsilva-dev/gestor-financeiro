package com.gestor.financeiro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name = "assistant_usage_daily")
@Getter @Setter @NoArgsConstructor
public class AssistantUsageDaily {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(name = "usage_date", nullable = false) private LocalDate usageDate;
    @Column(name = "external_calls", nullable = false) private Integer externalCalls;
    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6) private BigDecimal costUsd;
}
