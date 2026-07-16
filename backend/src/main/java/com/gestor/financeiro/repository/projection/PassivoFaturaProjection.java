package com.gestor.financeiro.repository.projection;

import java.math.BigDecimal;

public interface PassivoFaturaProjection {
    Long getCartaoId();
    Long getContaFinanceiraId();
    BigDecimal getSaldoPassivo();
    BigDecimal getSaldoFaturas();
}
