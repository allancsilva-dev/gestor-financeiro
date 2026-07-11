package com.gestor.financeiro.model.enums;

public enum TipoFaturaLancamento {
    COMPRA,
    AJUSTE,
    ESTORNO,
    // Rollover de fatura (docs/SYSTEM_OVERVIEW.md, secao "Regra de produto: credito de
    // fatura e saldo devedor rolado"): gerados por FaturaService.liquidarFaturaAnterior,
    // nunca por acao direta do usuario. Nao possuem transacao associada.
    CREDITO_ANTERIOR,
    SALDO_DEVEDOR_ANTERIOR
}
