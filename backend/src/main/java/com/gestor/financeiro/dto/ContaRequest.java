package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.enums.TipoConta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ContaRequest {

    @NotBlank(message = "Campo obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @NotNull(message = "Campo obrigatório")
    private TipoConta tipo;

    @PositiveOrZero(message = "Limite total deve ser zero ou positivo")
    private BigDecimal limiteTotal;

    @Min(value = 1, message = "Dia de fechamento deve estar entre 1 e 31")
    @Max(value = 31, message = "Dia de fechamento deve estar entre 1 e 31")
    private Integer diaFechamento;

    @Min(value = 1, message = "Dia de vencimento deve estar entre 1 e 31")
    @Max(value = 31, message = "Dia de vencimento deve estar entre 1 e 31")
    private Integer diaVencimento;

    @Size(max = 7, message = "Cor deve ter no máximo 7 caracteres")
    private String cor;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public TipoConta getTipo() {
        return tipo;
    }

    public void setTipo(TipoConta tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getLimiteTotal() {
        return limiteTotal;
    }

    public void setLimiteTotal(BigDecimal limiteTotal) {
        this.limiteTotal = limiteTotal;
    }

    public Integer getDiaFechamento() {
        return diaFechamento;
    }

    public void setDiaFechamento(Integer diaFechamento) {
        this.diaFechamento = diaFechamento;
    }

    public Integer getDiaVencimento() {
        return diaVencimento;
    }

    public void setDiaVencimento(Integer diaVencimento) {
        this.diaVencimento = diaVencimento;
    }

    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }
}
