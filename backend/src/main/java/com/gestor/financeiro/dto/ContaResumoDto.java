package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Conta;

public record ContaResumoDto(
    Long id,
    String nome
) {
    public static ContaResumoDto fromEntity(Conta conta) {
        if (conta == null) {
            return null;
        }

        return new ContaResumoDto(
            conta.getId(),
            conta.getNome()
        );
    }
}
