package com.gestor.financeiro.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaAlerta {
    private String categoriaNome;
    private BigDecimal gastoAtual;
    private BigDecimal gastoMedio;
    private BigDecimal variacaoPercentual;
    private boolean acimaMedia;
}
