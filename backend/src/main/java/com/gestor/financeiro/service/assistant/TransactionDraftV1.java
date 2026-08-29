package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gestor.financeiro.model.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Schema compartilhado pelos parsers e providers. Campos extras falham fechado. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TransactionDraftV1(
        String intent,
        TipoTransacao tipo,
        BigDecimal valor,
        String descricao,
        LocalDate data,
        String contaNome,
        String categoriaNome,
        String cartaoNome,
        Integer parcelas,
        List<String> missingFields) {

    public TransactionDraftV1 {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
