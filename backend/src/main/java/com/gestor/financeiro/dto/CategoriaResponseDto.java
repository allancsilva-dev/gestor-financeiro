package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Categoria;
import java.math.BigDecimal;

public record CategoriaResponseDto(
    Long id,
    String nome,
    String cor,
    String icone,
    BigDecimal valorEsperado,
    BigDecimal valorGasto,
    Boolean ativo
) {
    public static CategoriaResponseDto fromEntity(Categoria categoria) {
        return new CategoriaResponseDto(
            categoria.getId(),
            categoria.getNome(),
            categoria.getCor(),
            categoria.getIcone(),
            categoria.getValorEsperado(),
            categoria.getValorGasto(),
            categoria.getAtivo()
        );
    }
}
