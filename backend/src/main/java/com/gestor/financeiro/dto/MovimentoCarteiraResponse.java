package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.MovimentoCarteira;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentoCarteiraResponse(
        Long id,
        Long carteiraId,
        String carteiraNome,
        String tipo,
        BigDecimal valor,
        BigDecimal valorAssinado,
        String origem,
        String referenciaTipo,
        Long referenciaId,
        String descricao,
        LocalDateTime dataMovimento,
        BigDecimal saldoResultante,
        String idempotencyKey,
        String moeda,
        LocalDateTime createdAt
) {
    public static MovimentoCarteiraResponse fromEntity(MovimentoCarteira m) {
        return new MovimentoCarteiraResponse(
                m.getId(),
                m.getCarteira().getId(),
                m.getCarteira().getNome(),
                m.getTipo().name(),
                m.getValor(),
                m.getValorAssinado(),
                m.getOrigem().name(),
                m.getReferenciaTipo(),
                m.getReferenciaId(),
                m.getDescricao(),
                m.getDataMovimento(),
                m.getSaldoResultante(),
                m.getIdempotencyKey(),
                m.getMoeda(),
                m.getCreatedAt()
        );
    }
}
