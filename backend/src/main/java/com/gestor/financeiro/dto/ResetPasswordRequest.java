package com.gestor.financeiro.dto;

import com.gestor.financeiro.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public class ResetPasswordRequest {

    @NotBlank(message = "Campo obrigatório")
    private String token;

    @NotBlank(message = "Campo obrigatório")
    @ValidPassword
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