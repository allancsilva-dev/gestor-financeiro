package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Conta;

public record CartaoResumoDto(
    Long id,
    String nome
) {
    public static CartaoResumoDto fromEntity(Conta cartao) {
        if (cartao == null) {
            return null;
        }

        return new CartaoResumoDto(
            cartao.getId(),
            cartao.getNome()
        );
    }
}
