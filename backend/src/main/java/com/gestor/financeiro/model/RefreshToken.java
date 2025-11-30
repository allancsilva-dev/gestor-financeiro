package com.gestor.financeiro.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidade RefreshToken
 * 
 * Armazena tokens de atualização para renovação automática de JWT
 * Cada usuário pode ter múltiplos refresh tokens (web, mobile, diferentes dispositivos)
 * 
 * @author Equipe de Desenvolvimento
 * @version 1.0
 * @since 2024-11-17
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(nullable = false)
    private Boolean revogado = false;

    // ==========================================
    // CONSTRUTORES
    // ==========================================

    public RefreshToken() {
        this.dataCriacao = LocalDateTime.now();
        this.revogado = false;
    }

    public RefreshToken(Usuario usuario, String token, LocalDateTime dataExpiracao) {
        this.usuario = usuario;
        this.token = token;
        this.dataExpiracao = dataExpiracao;
        this.dataCriacao = LocalDateTime.now();
        this.revogado = false;
    }

    // ==========================================
    // MÉTODOS DE NEGÓCIO
    // ==========================================

    /**
     * Verifica se o token está expirado
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(this.dataExpiracao);
    }

    /**
     * Verifica se o token é válido (não expirado e não revogado)
     */
    public boolean isValido() {
        return !isExpirado() && !revogado;
    }

    /**
     * Revoga o token (usado no logout)
     */
    public void revogar() {
        this.revogado = true;
    }

    // ==========================================
    // GETTERS E SETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getDataExpiracao() {
        return dataExpiracao;
    }

    public void setDataExpiracao(LocalDateTime dataExpiracao) {
        this.dataExpiracao = dataExpiracao;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public Boolean getRevogado() {
        return revogado;
    }

    public void setRevogado(Boolean revogado) {
        this.revogado = revogado;
    }

    // ==========================================
    // EQUALS, HASHCODE, TOSTRING
    // ==========================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken)) return false;
        RefreshToken that = (RefreshToken) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RefreshToken{" +
                "id=" + id +
                ", usuario=" + (usuario != null ? usuario.getEmail() : "null") +
                ", token='" + token.substring(0, 20) + "...'" +
                ", dataExpiracao=" + dataExpiracao +
                ", revogado=" + revogado +
                '}';
    }
}
