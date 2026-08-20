package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Conta;

import java.math.BigDecimal;

public record CartaoResponse(
        Long id,
        Long contaFinanceiraId,
        String nome,
        BigDecimal limiteTotal,
        BigDecimal saldoDevedor,
        BigDecimal limiteDisponivel,
        Integer diaFechamento,
        Integer diaVencimento,
        Boolean ativo,
        String cor,
        String banco,
        String ultimosDigitos,
        String bandeira
) {
    public static CartaoResponse fromEntity(Conta cartao) {
        BigDecimal limite = cartao.getLimiteTotal() == null ? BigDecimal.ZERO : cartao.getLimiteTotal();
        BigDecimal saldo = cartao.getContaFinanceira() == null || cartao.getContaFinanceira().getSaldo() == null
                ? BigDecimal.ZERO : cartao.getContaFinanceira().getSaldo();
        return new CartaoResponse(
                cartao.getId(),
                cartao.getContaFinanceira() == null ? null : cartao.getContaFinanceira().getId(),
                cartao.getNome(), limite, saldo, limite.subtract(saldo),
                cartao.getDiaFechamento(), cartao.getDiaVencimento(), cartao.getAtivo(),
                cartao.getCor(), cartao.getBanco(),
                cartao.getUltimosDigitos(), cartao.getBandeira());
    }
}
