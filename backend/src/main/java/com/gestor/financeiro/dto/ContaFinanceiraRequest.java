package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.enums.LiquidezContaFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Contrato público canônico de conta financeira. */
public record ContaFinanceiraRequest(
        @NotBlank(message = "Campo obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotNull(message = "Campo obrigatório")
        NaturezaContaFinanceira natureza,

        @NotNull(message = "Campo obrigatório")
        SubtipoContaFinanceira subtipo,

        @NotNull(message = "Campo obrigatório")
        LiquidezContaFinanceira liquidez,

        @NotBlank(message = "Campo obrigatório")
        @Pattern(regexp = "[A-Z]{3}", message = "Moeda deve usar o código ISO 4217")
        String moeda,

        @Size(max = 100, message = "Banco deve ter no máximo 100 caracteres")
        String banco,

        @NotNull(message = "Campo obrigatório")
        @PositiveOrZero(message = "Saldo inicial deve ser zero ou positivo")
        BigDecimal saldoInicial
) {}
