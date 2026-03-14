package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.enums.TipoCarteira;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class CarteiraRequest {

    @NotBlank(message = "Campo obrigatório")
    private String nome;

    @NotNull(message = "Campo obrigatório")
    private TipoCarteira tipo;

    @NotNull(message = "Campo obrigatório")
    @PositiveOrZero(message = "Saldo deve ser zero ou positivo")
    private BigDecimal saldo;

    private String banco;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public TipoCarteira getTipo() {
        return tipo;
    }

    public void setTipo(TipoCarteira tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public String getBanco() {
        return banco;
    }

    public void setBanco(String banco) {
        this.banco = banco;
    }
}
