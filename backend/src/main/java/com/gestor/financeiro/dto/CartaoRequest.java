package com.gestor.financeiro.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CartaoRequest(
        @NotBlank(message = "Campo obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotNull(message = "Campo obrigatório")
        @PositiveOrZero(message = "Limite total deve ser zero ou positivo")
        BigDecimal limiteTotal,

        @NotNull(message = "Campo obrigatório")
        @Min(value = 1, message = "Dia de fechamento deve estar entre 1 e 31")
        @Max(value = 31, message = "Dia de fechamento deve estar entre 1 e 31")
        Integer diaFechamento,

        @NotNull(message = "Campo obrigatório")
        @Min(value = 1, message = "Dia de vencimento deve estar entre 1 e 31")
        @Max(value = 31, message = "Dia de vencimento deve estar entre 1 e 31")
        Integer diaVencimento,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB")
        String cor,

        @Size(max = 60, message = "Banco deve ter no máximo 60 caracteres")
        String banco
) {}
