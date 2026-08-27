package com.gestor.financeiro.dto;

import java.util.Map;

/** Perfil de mapeamento como o titular o vê. */
public record ImportMapeamentoResponse(
        Long id,
        String nome,
        String instituicao,
        String delimitador,
        Map<String, String> colunas
) {
}
