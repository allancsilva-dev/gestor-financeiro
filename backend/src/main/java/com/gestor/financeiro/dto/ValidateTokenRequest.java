package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ValidateTokenRequest {

    @NotBlank(message = "Campo obrigatório")
    @Size(max = 255, message = "Token inválido")
    private String token;

    public ValidateTokenRequest() {}

    public ValidateTokenRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
