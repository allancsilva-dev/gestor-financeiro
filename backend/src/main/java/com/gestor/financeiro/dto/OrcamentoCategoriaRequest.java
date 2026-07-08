package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class OrcamentoCategoriaRequest {

    @NotNull(message = "Categoria é obrigatória")
    private Long categoriaId;

    @NotNull(message = "Valor limite é obrigatório")
    private BigDecimal valorLimite;

    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }

    public BigDecimal getValorLimite() { return valorLimite; }
    public void setValorLimite(BigDecimal valorLimite) { this.valorLimite = valorLimite; }
}
