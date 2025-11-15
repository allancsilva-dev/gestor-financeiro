package com.gestor.financeiro.model.enums;

public enum StatusPagamento {
    PAGO("Pago"),
    PENDENTE("Pendente"),
    ATRASADO("Atrasado"),
    CANCELADO("Cancelado");
    
    private String descricao;
    
    StatusPagamento(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }
}