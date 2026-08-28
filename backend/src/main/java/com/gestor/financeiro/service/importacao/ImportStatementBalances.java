package com.gestor.financeiro.service.importacao;

import java.math.BigDecimal;

public record ImportStatementBalances(BigDecimal opening, BigDecimal closing) {
    public static ImportStatementBalances unavailable() {
        return new ImportStatementBalances(null, null);
    }
}
