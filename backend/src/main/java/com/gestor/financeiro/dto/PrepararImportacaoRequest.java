package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotNull;

/** Conta financeira de destino do lote. Sem ela não existe lançamento. */
public record PrepararImportacaoRequest(@NotNull Long contaFinanceiraId) {
}
