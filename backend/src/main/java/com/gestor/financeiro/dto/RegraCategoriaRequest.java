package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Regra nova. `tipoCasamento` ausente vale CONTEM; `tipoTransacao` ausente vale para entrada e
 * saída. Não existe campo de expressão regular — ver `RegraCategoriaService`.
 */
public record RegraCategoriaRequest(
        @NotNull String padrao,
        String tipoCasamento,
        String tipoTransacao,
        @NotNull Long categoriaId,
        Integer prioridade
) {
}
