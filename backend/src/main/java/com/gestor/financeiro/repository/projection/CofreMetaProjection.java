package com.gestor.financeiro.repository.projection;

import java.math.BigDecimal;

public interface CofreMetaProjection {
    Long getMetaId();
    Long getUsuarioId();
    BigDecimal getValorReservado();
    Long getCofreId();
    Long getCofreUsuarioId();
    String getCofreSubtipo();
    BigDecimal getCofreSaldo();
}
