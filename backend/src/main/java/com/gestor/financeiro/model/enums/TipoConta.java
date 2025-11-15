package com.gestor.financeiro.model.enums;

public enum TipoConta {
    CREDITO("Crédito"),
    DEBITO("Débito"),
    DINHEIRO("Dinheiro"),
    POUPANCA("Poupança");
    
    private String descricao;
    
    TipoConta(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
}