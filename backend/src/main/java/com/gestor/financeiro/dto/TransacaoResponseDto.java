package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransacaoResponseDto(
    Long id,
    String descricao,
    BigDecimal valorTotal,
    TipoTransacao tipo,
    LocalDate data,
    StatusPagamento status,
    Boolean parcelado,
    Integer totalParcelas,
    BigDecimal valorParcela,
    String observacoes,
    Boolean recorrente,
    ContaResumoDto conta,
    CategoriaResumoDto categoria
) {
    public static TransacaoResponseDto fromEntity(Transacao transacao) {
        return new TransacaoResponseDto(
            transacao.getId(),
            transacao.getDescricao(),
            transacao.getValorTotal(),
            transacao.getTipo(),
            transacao.getData(),
            transacao.getStatus(),
            transacao.getParcelado(),
            transacao.getTotalParcelas(),
            transacao.getValorParcela(),
            transacao.getObservacoes(),
            transacao.getRecorrente(),
            ContaResumoDto.fromEntity(transacao.getConta()),
            CategoriaResumoDto.fromEntity(transacao.getCategoria())
        );
    }
}
