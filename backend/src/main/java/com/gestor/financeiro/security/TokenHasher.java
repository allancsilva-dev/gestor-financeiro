package com.gestor.financeiro.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e hash de tokens opacos (refresh token, reset de senha).
 *
 * O valor cru (256 bits de SecureRandom) vai ao cliente uma única vez; no banco
 * fica somente o SHA-256 hex. SHA-256 puro sem salt é suficiente aqui: a entropia
 * do valor torna força bruta inviável e o hash determinístico preserva o lookup
 * indexado por coluna única.
 */
public final class TokenHasher {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenHasher() {
    }

    public static String gerarValor() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
