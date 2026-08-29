package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.enums.EstadoConciliacaoConta;
import com.gestor.financeiro.model.enums.LiquidezContaFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.OrigemDadosConta;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;

import java.math.BigDecimal;

/** Resposta canônica sem campos do modelo legado. */
public record ContaFinanceiraResponse(
        Long id,
        String nome,
        NaturezaContaFinanceira natureza,
        SubtipoContaFinanceira subtipo,
        LiquidezContaFinanceira liquidez,
        String moeda,
        String banco,
        BigDecimal saldo,
        OrigemDadosConta origemDados,
        EstadoConciliacaoConta estadoConciliacao,
        boolean principal
) {
    public static ContaFinanceiraResponse fromEntity(Carteira conta) {
        return new ContaFinanceiraResponse(
                conta.getId(), conta.getNome(), conta.getNatureza(), conta.getSubtipo(),
                conta.getLiquidez(), conta.getMoeda(), conta.getBanco(), conta.getSaldo(),
                conta.getOrigemDados(), conta.getEstadoConciliacao(), conta.isPrincipal());
    }
}
