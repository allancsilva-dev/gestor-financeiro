package com.gestor.financeiro.dto;

import com.gestor.financeiro.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public class AlterarSenhaRequest {

    @NotBlank(message = "Senha atual obrigatória")
    private String senhaAtual;

    @ValidPassword
    private String novaSenha;

    public String getSenhaAtual() {
        return senhaAtual;
    }

    public void setSenhaAtual(String senhaAtual) {
        this.senhaAtual = senhaAtual;
    }

    public String getNovaSenha() {
        return novaSenha;
    }

    public void setNovaSenha(String novaSenha) {
        this.novaSenha = novaSenha;
    }
}
