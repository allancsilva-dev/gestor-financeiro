package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.RecorrenciaCandidata;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Padrão detectado, como o titular decide sobre ele. */
public record RecorrenciaCandidataResponse(
        Long id,
        String descricao,
        String tipo,
        BigDecimal valorMedio,
        int diaTipico,
        int ocorrencias,
        LocalDate primeiraData,
        LocalDate ultimaData,
        Long categoriaId,
        String categoriaNome
) {
    public static RecorrenciaCandidataResponse de(RecorrenciaCandidata candidata) {
        return new RecorrenciaCandidataResponse(
                candidata.getId(),
                candidata.getDescricaoExibicao(),
                candidata.getTipo().name(),
                candidata.getValorMedio(),
                candidata.getDiaTipico(),
                candidata.getOcorrencias(),
                candidata.getPrimeiraData(),
                candidata.getUltimaData(),
                candidata.getCategoria() == null ? null : candidata.getCategoria().getId(),
                candidata.getCategoria() == null ? null : candidata.getCategoria().getNome());
    }
}
