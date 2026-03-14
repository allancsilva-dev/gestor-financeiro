package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Categoria;

public record CategoriaResumoDto(
    Long id,
    String nome,
    String cor,
    String icone
) {
    public static CategoriaResumoDto fromEntity(Categoria categoria) {
        if (categoria == null) {
            return null;
        }

        return new CategoriaResumoDto(
            categoria.getId(),
            categoria.getNome(),
            categoria.getCor(),
            categoria.getIcone()
        );
    }
}
