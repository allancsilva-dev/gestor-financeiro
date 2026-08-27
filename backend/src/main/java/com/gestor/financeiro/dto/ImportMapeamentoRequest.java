package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Mapeamento de colunas. `colunas` liga o campo canônico (`date`, `description`, `amount`,
 * `currency`, `direction`, `externalId`) ao nome da coluna no arquivo; data e valor são obrigatórios.
 */
public record ImportMapeamentoRequest(
        @NotBlank String nome,
        String instituicao,
        String delimitador,
        Map<String, String> colunas
) {
}
