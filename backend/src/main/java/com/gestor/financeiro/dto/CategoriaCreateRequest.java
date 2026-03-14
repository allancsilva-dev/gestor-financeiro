package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CategoriaCreateRequest(
    @NotBlank(message = "Campo obrigatório")
    @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
    String nome,

    @Size(max = 7, message = "Cor deve ter no máximo 7 caracteres")
    String cor,

    @Size(max = 10, message = "Ícone deve ter no máximo 10 caracteres")
    String icone,

    @PositiveOrZero(message = "Valor esperado deve ser zero ou positivo")
    BigDecimal valorEsperado
) {}
