package com.gestor.financeiro.dto;

import com.gestor.financeiro.service.MetricasService.Metricas;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta unica da home (V42). Existe para a tela redesenhada caber em dois
 * requests (este + a pagina de operacoes) em vez dos cinco que precisaria
 * montando peca por peca, mantendo o espirito do orcamento do PR-F3-07.
 *
 * `saldoEmConta` e `saldoEmCartoes` sao recortes de exibicao do cabecalho —
 * as metricas oficiais continuam vindo inteiras em `metricas` (ADR-0013), sem
 * semantica duplicada.
 */
public record HomeResponse(
        Metricas metricas,
        BigDecimal saldoEmConta,
        BigDecimal saldoEmCartoes,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        BigDecimal totalFaturas,
        List<ParcelaAgendadaDto> parcelasAgendadas,
        List<CategoriaResumoDto> categorias,
        long naoLidas
) {
}
