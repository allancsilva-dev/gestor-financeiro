package com.gestor.financeiro.dto;

import com.gestor.financeiro.validation.ValidPassword;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Campo obrigatorio")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    private String nome;

    @NotBlank(message = "Campo obrigatorio")
    @Email(message = "Email invalido")
    private String email;

    @NotBlank(message = "Campo obrigatorio")
    @ValidPassword
    private String password;

    @NotBlank(message = "Campo obrigatorio")
    private String confirmPassword;

    @AssertTrue(message = "Senhas nao coincidem")
    public boolean isPasswordMatch() {
        if (password == null || confirmPassword == null) return true;
        return password.equals(confirmPassword);
    }

    public RegisterRequest() {}

    public RegisterRequest(String nome, String email, String password) {
        this.nome = nome;
        this.email = email;
        this.password = password;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

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

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}