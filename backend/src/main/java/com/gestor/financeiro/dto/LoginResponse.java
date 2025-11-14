package com.gestor.financeiro.dto;

public class LoginResponse {
    private String message;
    private boolean success;
    private String token;

    // Construtor sem token (para erros)
    public LoginResponse(String message, boolean success) {
        this.message = message;
        this.success = success;
    }

    // Construtor com token (para sucesso)
    public LoginResponse(String message, boolean success, String token) {
        this.message = message;
        this.success = success;
        this.token = token;
    }

    // Getters e Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}