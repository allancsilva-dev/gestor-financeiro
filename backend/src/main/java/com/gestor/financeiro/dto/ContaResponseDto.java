package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.enums.TipoConta;
import java.math.BigDecimal;

public record ContaResponseDto(
    Long id,
    String nome,
    TipoConta tipo,
    BigDecimal limiteTotal,
    BigDecimal valorGasto,
    BigDecimal saldoAtual,
    Integer diaFechamento,
    Integer diaVencimento,
    Boolean ativo,
    String cor
) {
    public static ContaResponseDto fromEntity(Conta conta) {
        return new ContaResponseDto(
            conta.getId(),
            conta.getNome(),
            conta.getTipo(),
            conta.getLimiteTotal(),
            conta.getValorGasto(),
            conta.getSaldoAtual(),
            conta.getDiaFechamento(),
            conta.getDiaVencimento(),
            conta.getAtivo(),
            conta.getCor()
        );
    }
}
