package com.gestor.financeiro.repository.projection;

import java.math.BigDecimal;

public interface LedgerSaldoProjection {

    Long getCarteiraId();

    Long getUsuarioId();

    BigDecimal getSaldoMaterializado();

    BigDecimal getSaldoLedger();
}
