package com.gestor.financeiro.dto;

/**
 * Sugestao deterministica de categoria (PR-F3-02). Sem resultado: criterio
 * NENHUMA com categoria nula, sempre HTTP 200.
 */
public record SugestaoCategoriaResponse(
        String criterio,            // DESCRICAO_IGUAL | MAIS_USADA_90_DIAS | NENHUMA
        CategoriaResumoDto categoria
) {
}
