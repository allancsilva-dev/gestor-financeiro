package com.gestor.financeiro.service;

public record LedgerBackfillResult(
        int carteirasAvaliadas,
        int movimentosCriados,
        int carteirasComBackfillExistente,
        int carteirasSemBackfillNecessario
) {
}
