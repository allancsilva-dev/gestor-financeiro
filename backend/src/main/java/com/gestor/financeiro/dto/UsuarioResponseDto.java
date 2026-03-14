package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.Usuario;

public record UsuarioResponseDto(
    Long id,
    String nome,
    String email
) {
    public static UsuarioResponseDto fromEntity(Usuario usuario) {
        return new UsuarioResponseDto(
            usuario.getId(),
            usuario.getNome(),
            usuario.getEmail()
        );
    }
}
