package com.gestor.financeiro.model.enums;

public enum TipoAtivo {
    ACAO("Ação"),
    FII("Fundo Imobiliário"),
    RENDA_FIXA("Renda Fixa"),
    CRIPTO("Criptomoeda"),
    OUTRO("Outro");

    private String descricao;

    TipoAtivo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
