package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.FaturaLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Item do carrossel "Parcelas Agendadas". Traz numero/total, cartao e
 * categoria estruturados para a UI montar "iPhone 15 (6/10)" e
 * "Mastercard .... 8034" sem parsear texto.
 */
public record ParcelaAgendadaDto(
        Long id,
        Long transacaoId,
        String descricao,
        Integer numeroParcela,
        Integer totalParcelas,
        BigDecimal valor,
        LocalDate vencimento,
        CartaoResumoDto cartao,
        CategoriaResumoDto categoria,
        boolean atrasada
) {
    public static ParcelaAgendadaDto fromEntity(FaturaLancamento fl, LocalDate hoje) {
        var transacao = fl.getTransacao();
        var vencimento = fl.getFatura().getDataVencimento();
        return new ParcelaAgendadaDto(
                fl.getId(),
                transacao == null ? null : transacao.getId(),
                transacao == null ? fl.getDescricao() : transacao.getDescricao(),
                fl.getParcelaNumero(),
                fl.getTotalParcelas(),
                fl.getValor(),
                vencimento,
                CartaoResumoDto.fromEntity(fl.getFatura().getConta()),
                transacao == null ? null : CategoriaResumoDto.fromEntity(transacao.getCategoria()),
                vencimento != null && vencimento.isBefore(hoje));
    }
}
