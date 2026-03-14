package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.enums.TipoCarteira;
import java.math.BigDecimal;

public record CarteiraResponseDto(
    Long id,
    String nome,
    TipoCarteira tipo,
    BigDecimal saldo,
    String banco
) {
    public static CarteiraResponseDto fromEntity(Carteira carteira) {
        return new CarteiraResponseDto(
            carteira.getId(),
            carteira.getNome(),
            carteira.getTipo(),
            carteira.getSaldo(),
            carteira.getBanco()
        );
    }
}
