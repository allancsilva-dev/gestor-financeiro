package com.gestor.financeiro.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Comando de transferencia interna (ADR-0009): duas contas do mesmo usuario,
 * origem != destino, nunca receita/despesa/resultado mensal.
 */
public record TransferirCommand(
        Long usuarioId,
        Long contaOrigemId,
        Long contaDestinoId,
        BigDecimal valor,
        String descricao,
        String idempotencyKey,
        LocalDateTime dataOperacao
) {
    public TransferirCommand {
        if (usuarioId == null || contaOrigemId == null || contaDestinoId == null) {
            throw new IllegalArgumentException("usuarioId, contaOrigemId e contaDestinoId são obrigatórios");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
    }
}
