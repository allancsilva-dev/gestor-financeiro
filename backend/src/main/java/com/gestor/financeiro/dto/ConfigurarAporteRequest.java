package com.gestor.financeiro.dto;

import java.math.BigDecimal;

/**
 * Configuração do aporte automático. `ativo=false` desliga e limpa o resto — o app parar de mover
 * dinheiro não pode depender de o cliente lembrar de limpar campo.
 */
public record ConfigurarAporteRequest(
        Boolean ativo,
        Short dia,
        Long carteiraId,
        BigDecimal valorMensal
) {
}
