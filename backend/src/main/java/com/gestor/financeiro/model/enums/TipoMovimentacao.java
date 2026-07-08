package com.gestor.financeiro.model.enums;

public enum TipoMovimentacao {
    COMPRA("Compra"),
    VENDA("Venda"),
    DIVIDENDO("Dividendo"),
    BONIFICACAO("Bonificação");

    private String descricao;

    TipoMovimentacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
