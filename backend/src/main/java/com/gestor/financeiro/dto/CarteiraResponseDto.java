package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.enums.EstadoConciliacaoConta;
import com.gestor.financeiro.model.enums.LiquidezContaFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.OrigemDadosConta;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import java.math.BigDecimal;

public record CarteiraResponseDto(
    Long id,
    String nome,
    TipoCarteira tipo,
    BigDecimal saldo,
    String banco,
    NaturezaContaFinanceira natureza,
    SubtipoContaFinanceira subtipo,
    LiquidezContaFinanceira liquidez,
    OrigemDadosConta origemDados,
    EstadoConciliacaoConta estadoConciliacao,
    String moeda
) {
    public static CarteiraResponseDto fromEntity(Carteira carteira) {
        return new CarteiraResponseDto(
            carteira.getId(),
            carteira.getNome(),
            carteira.getTipo(),
            carteira.getSaldo(),
            carteira.getBanco(),
            carteira.getNatureza(),
            carteira.getSubtipo(),
            carteira.getLiquidez(),
            carteira.getOrigemDados(),
            carteira.getEstadoConciliacao(),
            carteira.getMoeda()
        );
    }
}
