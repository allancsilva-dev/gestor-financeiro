package com.gestor.financeiro.service;

import com.gestor.financeiro.model.enums.OrigemMovimentoCarteira;
import com.gestor.financeiro.model.enums.TipoMovimentoCarteira;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RegistrarMovimentoCommand(
        Long usuarioId,
        Long carteiraId,
        TipoMovimentoCarteira tipo,
        BigDecimal valor,
        Direcao direcao,
        OrigemMovimentoCarteira origem,
        String referenciaTipo,
        Long referenciaId,
        String descricao,
        String idempotencyKey,
        LocalDateTime dataMovimento,
        boolean permitirSaldoNegativo
) {
    public enum Direcao {
        ENTRADA,
        SAIDA
    }

    public RegistrarMovimentoCommand {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("valor deve ser positivo");
        }
    }
}
