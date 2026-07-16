package com.gestor.financeiro.model.enums;

/**
 * Visoes nomeadas de leitura financeira (ADR-0010, PR-F2-10).
 * COMPRA: valor total na data da compra, qualquer meio de pagamento.
 * COMPETENCIA: gasto atribuido ao mes de competencia (cartao pela data da
 * compra via FaturaLancamento; nunca pela data do pagamento da fatura).
 * CAIXA: quando o dinheiro se move (movimentos de contas ATIVO; pagamento de
 * fatura conta aqui, compra de cartao nao). Transferencias, reservas de meta e
 * investimento ficam fora de todas as visoes de consumo.
 */
public enum VisaoFinanceira {
    COMPRA,
    COMPETENCIA,
    CAIXA
}
