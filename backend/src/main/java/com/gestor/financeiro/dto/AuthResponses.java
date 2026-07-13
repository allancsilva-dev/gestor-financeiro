package com.gestor.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class AuthResponses {
    private AuthResponses() {}

    public record Register(Long id, String nome, String email) {}
    public record Usuario(Long id, String nome, String email, boolean onboardingCompleto) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Session(String message, boolean success, String accessToken, Usuario usuario,
                          String refreshToken, String csrfToken) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Refresh(String accessToken, String refreshToken, String csrfToken) {}
    public record Message(String message) {}
}
