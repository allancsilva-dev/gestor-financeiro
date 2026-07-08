package com.gestor.financeiro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightsResponse {
    private BigDecimal gastoMesAtual;
    private BigDecimal gastoMedioMensal;
    private BigDecimal variacaoPercentual;
    private BigDecimal previsaoSaldoFinal;
    private List<CategoriaAlerta> categoriasAlerta;
    private List<String> recomendacoes;
    private String resumo;
}
