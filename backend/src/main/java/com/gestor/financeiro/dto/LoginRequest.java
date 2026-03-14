package com.gestor.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Campo obrigatório")
    @Email(message = "Email inválido")
    private String email;

    // Mantém compatibilidade aceitando tanto "password" quanto "senha".
    @NotBlank(message = "Campo obrigatório")
    @JsonAlias({"senha", "password"})
    private String password;

    // Construtores
    public LoginRequest() {}

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // Getters e Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}