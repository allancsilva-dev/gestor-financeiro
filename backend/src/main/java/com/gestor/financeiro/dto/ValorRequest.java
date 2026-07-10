package com.gestor.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class ValorRequest {

    @NotNull(message = "Campo obrigatório")
    @Positive(message = "Valor deve ser positivo")
    @JsonAlias({"valor", "valorPago"})
    private BigDecimal valor;

    private Long carteiraId;

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Long getCarteiraId() {
        return carteiraId;
    }

    public void setCarteiraId(Long carteiraId) {
        this.carteiraId = carteiraId;
    }
}
