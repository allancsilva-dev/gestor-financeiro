package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class AjusteCarteiraRequest {

    @NotBlank(message = "Campo obrigatório")
    private String tipo;

    @NotNull(message = "Campo obrigatório")
    @Positive(message = "Valor deve ser positivo")
    private BigDecimal valor;

    private String descricao;

    public AjusteCarteiraRequest() {
    }

    public AjusteCarteiraRequest(String tipo, BigDecimal valor, String descricao) {
        this.tipo = tipo;
        this.valor = valor;
        this.descricao = descricao;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
}
