package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;

/** Aparelho que aceita receber aviso. O token vem do Expo, no cliente. */
public record RegistrarDispositivoRequest(
        @NotBlank String pushToken,
        @NotBlank String plataforma
) {
}
