package com.gestor.financeiro.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProjecaoResponse(
    BigDecimal saldoAtual,
    List<ProjecaoMensalDto> meses
) {}
