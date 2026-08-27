package com.gestor.financeiro.dto;

/**
 * Destino do lote: conta de caixa (extrato) ou cartão (fatura). Exatamente um dos dois — o arquivo
 * pertence a uma dessas duas histórias, e misturá-las duplicaria o lançamento.
 */
public record PrepararImportacaoRequest(Long contaFinanceiraId, Long cartaoId) {
}
