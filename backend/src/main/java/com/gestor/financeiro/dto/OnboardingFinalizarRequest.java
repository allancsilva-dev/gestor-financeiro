package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contrato canonico do onboarding (PR-F2-19): objeto cartao (sem alias conta)
 * e carteira com subtipo canonico (TipoCarteira removido no contract V41).
 * Onboarding minimo (PR-F3-03): so carteira e obrigatoria; cartao e categorias
 * nulos, ausentes ou lista vazia sao no-op. Payload completo segue valido.
 */
public record OnboardingFinalizarRequest(
    @NotNull(message = "Carteira obrigatória")
    @Valid
    CarteiraInicial carteira,

    @Valid
    CartaoInicial cartao,

    @Valid
    List<CategoriaInicial> categorias,

    @Valid
    RendaInicial renda,

    @Valid
    MetaInicial meta
) {
    public record CarteiraInicial(
        @NotBlank(message = "Campo obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotNull(message = "Campo obrigatório")
        SubtipoContaFinanceira subtipo,

        @NotNull(message = "Campo obrigatório")
        @PositiveOrZero(message = "Saldo deve ser zero ou positivo")
        BigDecimal saldo,

        @Size(max = 100, message = "Banco deve ter no máximo 100 caracteres")
        String banco
    ) {}

    public record CartaoInicial(
        @NotBlank(message = "Campo obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

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

        @Size(max = 7, message = "Cor deve ter no máximo 7 caracteres")
        String cor,

        @Size(max = 60, message = "Banco deve ter no máximo 60 caracteres")
        String banco
    ) {}

    public record CategoriaInicial(
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

    public record RendaInicial(
        @NotBlank(message = "Campo obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotNull(message = "Campo obrigatório")
        @Positive(message = "Valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "Campo obrigatório")
        @Min(value = 1, message = "Dia de vencimento deve estar entre 1 e 31")
        @Max(value = 31, message = "Dia de vencimento deve estar entre 1 e 31")
        Integer diaVencimento
    ) {}

    public record MetaInicial(
        @NotBlank(message = "Campo obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String nome,

        @NotNull(message = "Campo obrigatório")
        @Positive(message = "Valor total deve ser positivo")
        BigDecimal valorTotal,

        @PositiveOrZero(message = "Valor mensal deve ser zero ou positivo")
        BigDecimal valorMensal,

        LocalDate dataLimite,

        @Size(max = 7, message = "Cor deve ter no máximo 7 caracteres")
        String cor,

        @Size(max = 20, message = "Ícone deve ter no máximo 20 caracteres")
        String icone,

        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        String descricao
    ) {}
}
