package com.gestor.financeiro.exception;

/**
 * Refresh token que não pode mais ser honrado: expirado, revogado ou desconhecido.
 *
 * <p>É falha de autenticação (401), não erro de negócio. O cliente precisa
 * distinguir "sessão morreu, mande para o login" de "regra de negócio recusou",
 * e antes disso o mobile ficava preso com credenciais mortas no SecureStore.</p>
 */
public class SessaoExpiradaException extends RuntimeException {

    public SessaoExpiradaException(String message) {
        super(message);
    }
}
