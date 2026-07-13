package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public final class DashboardDtos {
    private DashboardDtos() {}
    public record Resumo(BigDecimal totalEntradas, BigDecimal totalSaidas, BigDecimal saldo,
                         long totalCategorias, long totalContas, long totalMetas,
                         long totalContasFixas, BigDecimal saldoCarteiras) {}
    public record Categoria(String categoria, BigDecimal valor, String cor, BigDecimal percentual) {}
    public record Evolucao(String mes, BigDecimal entradas, BigDecimal saidas, BigDecimal saldo) {}
    public record Comparacao(String periodo, BigDecimal entradas, BigDecimal saidas) {}
}
