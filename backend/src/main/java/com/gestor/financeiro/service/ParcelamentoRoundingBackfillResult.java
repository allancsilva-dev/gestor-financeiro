package com.gestor.financeiro.service;

import java.math.BigDecimal;
import java.util.List;

public record ParcelamentoRoundingBackfillResult(
        boolean dryRun,
        int transacoesComResiduoEmParcelas,
        int transacoesComResiduoEmFaturas,
        int parcelasCorrigidas,
        int lancamentosCorrigidos,
        List<Detalhe> detalhes
) {
    public record Detalhe(
            Long transacaoId,
            Alvo alvo,
            BigDecimal valorTotalTransacao,
            BigDecimal somaAntes,
            BigDecimal diferencaAplicada,
            Long registroCorrigidoId
    ) {
    }

    public enum Alvo {
        PARCELA,
        FATURA_LANCAMENTO
    }
}
