package com.gestor.financeiro.service;

import com.gestor.financeiro.model.enums.OrigemOperacaoFinanceira;
import com.gestor.financeiro.model.enums.PoliticaOperacao;
import com.gestor.financeiro.model.enums.TipoOperacaoFinanceira;

import java.time.LocalDateTime;

/**
 * Comando de criacao de operacao financeira (ADR-0009).
 * requestPayload e a representacao canonica do pedido usada para detectar
 * reutilizacao de idempotencyKey com conteudo diferente (HTTP 409).
 */
public record CriarOperacaoCommand(
        Long usuarioId,
        TipoOperacaoFinanceira tipo,
        PoliticaOperacao politica,
        OrigemOperacaoFinanceira origem,
        LocalDateTime dataOperacao,
        String idempotencyKey,
        String requestPayload,
        String descricao,
        Long estornoDeId
) {
    public CriarOperacaoCommand {
        if (usuarioId == null || tipo == null) {
            throw new IllegalArgumentException("usuarioId e tipo são obrigatórios");
        }
    }
}
