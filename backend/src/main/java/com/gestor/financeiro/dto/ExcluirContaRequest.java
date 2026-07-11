package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;

public class ExcluirContaRequest {

    @NotBlank(message = "Senha obrigatória para excluir a conta")
    private String senha;

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}
