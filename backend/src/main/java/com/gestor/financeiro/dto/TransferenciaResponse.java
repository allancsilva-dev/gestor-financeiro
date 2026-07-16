package com.gestor.financeiro.dto;

import com.gestor.financeiro.service.TransferenciaService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferenciaResponse(
        Long operacaoId,
        String status,
        BigDecimal valor,
        String descricao,
        LocalDateTime dataOperacao,
        MovimentoCarteiraResponse saida,
        MovimentoCarteiraResponse entrada
) {
    public static TransferenciaResponse from(TransferenciaService.Resultado resultado) {
        return new TransferenciaResponse(
                resultado.operacao().getId(),
                resultado.operacao().getStatus().name(),
                resultado.entrada().getValor(),
                resultado.operacao().getDescricao(),
                resultado.operacao().getDataOperacao(),
                MovimentoCarteiraResponse.fromEntity(resultado.saida()),
                MovimentoCarteiraResponse.fromEntity(resultado.entrada())
        );
    }
}
