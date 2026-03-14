package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CategoriaRequest {

    @NotBlank(message = "Campo obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    private String nome;

    @Size(max = 7, message = "Cor deve ter no máximo 7 caracteres")
    private String cor;

    @Size(max = 10, message = "Ícone deve ter no máximo 10 caracteres")
    private String icone;

    @PositiveOrZero(message = "Valor esperado deve ser zero ou positivo")
    private BigDecimal valorEsperado;
    
    // Getters e Setters
    public String getNome() {
        return nome;
    }
    
    public void setNome(String nome) {
        this.nome = nome;
    }
    
    public String getCor() {
        return cor;
    }
    
    public void setCor(String cor) {
        this.cor = cor;
    }
    
    public String getIcone() {
        return icone;
    }
    
    public void setIcone(String icone) {
        this.icone = icone;
    }
    
    public BigDecimal getValorEsperado() {
        return valorEsperado;
    }
    
    public void setValorEsperado(BigDecimal valorEsperado) {
        this.valorEsperado = valorEsperado;
    }
}