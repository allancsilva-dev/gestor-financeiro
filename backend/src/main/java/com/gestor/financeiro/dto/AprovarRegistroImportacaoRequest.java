package com.gestor.financeiro.dto;

/**
 * Aprovação de uma linha na revisão.
 *
 * <p>`criarRegra` é explícito de propósito: transformar cada correção em regra automaticamente
 * encheria a lista do usuário de regras que ele não pediu e mudaria a categorização de lançamentos
 * futuros sem ele saber.</p>
 */
public record AprovarRegistroImportacaoRequest(Long categoriaId, Boolean criarRegra) {
}
