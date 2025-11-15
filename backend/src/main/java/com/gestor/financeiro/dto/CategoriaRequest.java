package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public class CategoriaRequest {
    
    private String nome;
    private String cor;
    private String icone;
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