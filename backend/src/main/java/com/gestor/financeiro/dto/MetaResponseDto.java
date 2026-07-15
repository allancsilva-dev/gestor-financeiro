package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.enums.StatusMeta;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MetaResponseDto(
    Long id,
    String nome,
    BigDecimal valorTotal,
    BigDecimal valorReservado,
    BigDecimal valorMensal,
    LocalDate dataInicio,
    LocalDate dataPrevista,
    LocalDate dataConclusao,
    StatusMeta status,
    // deprecado: use `status`; mantido para clientes publicados
    Boolean ativa,
    String cor,
    String icone,
    String descricao
) {
    public static MetaResponseDto fromEntity(Meta meta) {
        return new MetaResponseDto(
            meta.getId(),
            meta.getNome(),
            meta.getValorTotal(),
            meta.getValorReservado(),
            meta.getValorMensal(),
            meta.getDataInicio(),
            meta.getDataPrevista(),
            meta.getDataConclusao(),
            meta.getStatus(),
            meta.getAtiva(),
            meta.getCor(),
            meta.getIcone(),
            meta.getDescricao()
        );
    }
}
