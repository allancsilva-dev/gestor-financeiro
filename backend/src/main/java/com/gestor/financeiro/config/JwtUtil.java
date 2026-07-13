package com.gestor.financeiro.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitário JWT para geração e validação de tokens
 * 
 * SEGURANÇA:
 * - Secret key vem de variável de ambiente
 * - Access token expira em 15 minutos
 * - Usa HS256 para assinatura
 * 
 * @version 2.0 (Seguro para produção)
 */
@Component
public class JwtUtil {

    // ✅ CORRIGIDO: Secret vem de variável de ambiente
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    // ✅ CORRIGIDO: 15 minutos (900000 ms) ao invés de 24 horas
    @Value("${jwt.expiration:900000}")
    private long EXPIRATION_TIME;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, email);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRATION_TIME))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Boolean validateToken(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return (tokenEmail.equals(email) && !isTokenExpired(token));
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    private Claims extractAllClaims(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
        if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Algoritmo JWT não permitido");
        }
        return parsed.getPayload();
    }
}
