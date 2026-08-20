package com.gestor.financeiro.model.enums;

/** Eventos que o sistema ja sabe detectar e que viram notificacao in-app. */
public enum TipoNotificacao {
    FATURA_VENCENDO("Fatura vencendo"),
    PARCELA_AGENDADA("Parcela agendada"),
    FALHA_SALDO("Recorrencia sem saldo"),
    ORCAMENTO_ESTOURADO("Orcamento estourado"),
    META_ATINGIDA("Meta atingida");

    private final String descricao;

    TipoNotificacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
