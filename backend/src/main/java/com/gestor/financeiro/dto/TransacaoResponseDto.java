package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.enums.EstadoConciliacaoTransacao;
import com.gestor.financeiro.model.enums.StatusPagamento;
import com.gestor.financeiro.model.enums.TipoTransacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    CartaoResumoDto cartao,
    CategoriaResumoDto categoria,
    EstadoConciliacaoTransacao estadoConciliacao,
    /**
     * Avisos que acompanham a operacao sem impedi-la (ex.: limite do cartao estourado).
     * Campo aditivo: nas leituras vem vazio, nunca nulo.
     */
    List<AlertaDto> alertas
) {
    /** Leitura: sem alerta. Alerta e coisa de operacao que acabou de acontecer. */
    public static TransacaoResponseDto fromEntity(Transacao transacao) {
        return fromEntity(transacao, List.of());
    }

    public static TransacaoResponseDto fromEntity(Transacao transacao, List<AlertaDto> alertas) {
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
            CartaoResumoDto.fromEntity(transacao.getConta()),
            CategoriaResumoDto.fromEntity(transacao.getCategoria()),
            transacao.getEstadoConciliacao(),
            alertas == null ? List.of() : alertas
        );
    }
}
