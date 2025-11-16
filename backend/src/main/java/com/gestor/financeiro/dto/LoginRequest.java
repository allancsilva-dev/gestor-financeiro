package com.gestor.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginRequest {
    
    private String email;
    
    // ✅ Aceita "senha" do JSON
    @JsonProperty("senha")
    private String senha;
    
    // ✅ Aceita "password" do JSON
    @JsonProperty("password")  
    private String password;

    // Construtores
    public LoginRequest() {}

    public LoginRequest(String email, String senha) {
        this.email = email;
        this.senha = senha;
    }

    // Getters e Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // ✅ Retorna "senha" se existir, senão retorna "password"
    public String getSenha() {
        return senha != null ? senha : password;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}