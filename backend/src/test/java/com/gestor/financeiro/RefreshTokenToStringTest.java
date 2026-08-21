package com.gestor.financeiro;

import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BACKLOG-0083: um log acidental do objeto vazava prefixo do hash do token e o
 * e-mail do usuario (PII). O toString passa a expor so identificadores.
 */
class RefreshTokenToStringTest {

    private static final String HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Test
    void naoExpoeHashDoTokenNemEmailDoUsuario() {
        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setEmail("pessoa@exemplo.com");

        RefreshToken refreshToken = new RefreshToken(usuario, HASH, LocalDateTime.now().plusDays(7));

        String texto = refreshToken.toString();
        assertFalse(texto.contains("pessoa@exemplo.com"), texto);
        assertFalse(texto.contains(HASH.substring(0, 20)), texto);
        assertTrue(texto.contains("usuarioId=42"), texto);
    }

    @Test
    void naoQuebraComEntidadeVazia() {
        assertDoesNotThrow(() -> new RefreshToken().toString());
    }
}
