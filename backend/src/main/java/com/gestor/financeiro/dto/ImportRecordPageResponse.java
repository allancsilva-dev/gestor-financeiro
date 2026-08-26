package com.gestor.financeiro.dto;

import java.util.List;

/**
 * Página por cursor. Lote pode ter dezenas de milhares de linhas: `OFFSET` faria o banco varrer
 * tudo de novo a cada página, então a paginação anda por `sourceLine`.
 */
public record ImportRecordPageResponse(
        List<ImportRecordResponse> registros,
        Integer proximaLinha
) {
}
