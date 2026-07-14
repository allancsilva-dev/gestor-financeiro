package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidarSenhaRequest {
    @NotBlank(message = "Senha obrigatória")
    private String senha;
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
}
