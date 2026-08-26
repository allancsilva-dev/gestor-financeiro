package com.gestor.financeiro.dto;

/** Aprovação de uma linha na revisão; categoria é opcional. */
public record AprovarRegistroImportacaoRequest(Long categoriaId) {
}
