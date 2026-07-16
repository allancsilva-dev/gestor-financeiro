package com.gestor.financeiro.model.enums;

public enum TipoCarteira {
    DINHEIRO,
    CONTA_BANCARIA,
    POUPANCA,
    /** Conta financeira passiva do cartao (ADR-0008); oculta na listagem legada. */
    CARTAO
}