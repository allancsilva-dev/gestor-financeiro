package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.enums.StatusPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.gestor.financeiro.model.enums.FrequenciaRecorrencia;
import com.gestor.financeiro.model.enums.TipoTransacao;

public record ContaFixaResponseDto(
    Long id,
    String nome,
    BigDecimal valorPlanejado,
    BigDecimal valorReal,
    Integer diaVencimento,
    LocalDate dataProximoVencimento,
    StatusPagamento status,
    Boolean recorrente,
    Boolean ativo,
    String observacoes,
    CategoriaResumoDto categoria,
    TipoTransacao tipo,
    Boolean execucaoAutomatica,
    CarteiraResumo carteira,
    CartaoResumo cartao,
    /** Periodicidade da cobranca (V72). */
    FrequenciaRecorrencia frequencia,
    /** Primeira cobranca da serie: fixa o dia da semana em SEMANAL/QUINZENAL e o mes do
     *  aniversario de BIMESTRAL a ANUAL (V73). Sempre null em MENSAL. */
    LocalDate dataAncora,
    /**
     * Avisos que acompanham a operacao sem impedi-la (ex.: limite do cartao estourado).
     * Campo aditivo: nas leituras vem vazio, nunca nulo.
     */
    List<AlertaDto> alertas
) {
    public record CarteiraResumo(Long id, String nome) {}
    /** Metadado de exibicao do cartao; nunca PAN. */
    public record CartaoResumo(Long id, String nome, String bandeira, String ultimosDigitos) {}
    /** Leitura: sem alerta. Alerta e coisa de operacao que acabou de acontecer. */
    public static ContaFixaResponseDto fromEntity(ContaFixa contaFixa) {
        return fromEntity(contaFixa, List.of());
    }

    public static ContaFixaResponseDto fromEntity(ContaFixa contaFixa, List<AlertaDto> alertas) {
        return new ContaFixaResponseDto(
            contaFixa.getId(),
            contaFixa.getNome(),
            contaFixa.getValorPlanejado(),
            contaFixa.getValorReal(),
            contaFixa.getDiaVencimento(),
            contaFixa.getDataProximoVencimento(),
            contaFixa.getStatus(),
            contaFixa.getRecorrente(),
            contaFixa.getAtivo(),
            contaFixa.getObservacoes(),
            CategoriaResumoDto.fromEntity(contaFixa.getCategoria()),
            contaFixa.getTipo(),
            contaFixa.getExecucaoAutomatica(),
            contaFixa.getCarteira() == null ? null : new CarteiraResumo(contaFixa.getCarteira().getId(), contaFixa.getCarteira().getNome()),
            contaFixa.getConta() == null ? null : new CartaoResumo(
                contaFixa.getConta().getId(),
                contaFixa.getConta().getNome(),
                contaFixa.getConta().getBandeira(),
                contaFixa.getConta().getUltimosDigitos()),
            contaFixa.getFrequencia(),
            contaFixa.getDataAncora(),
            alertas == null ? List.of() : alertas
        );
    }
}
