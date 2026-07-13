package com.gestor.financeiro.dto;

import java.math.BigDecimal;

public record MetaProgressoResponse(Long metaId, BigDecimal valorTotal, BigDecimal valorReservado,
                                    BigDecimal valorRestante, BigDecimal progresso) {}
