package com.gestor.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @NotBlank(message = "Campo obrigatório")
    private String token;

    @NotBlank(message = "Campo obrigatório")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String novaSenha;
    
    public ResetPasswordRequest() {}
    
    public ResetPasswordRequest(String token, String novaSenha) {
        this.token = token;
        this.novaSenha = novaSenha;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getNovaSenha() {
        return novaSenha;
    }
    
    public void setNovaSenha(String novaSenha) {
        this.novaSenha = novaSenha;
    }
}