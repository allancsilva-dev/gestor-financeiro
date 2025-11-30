package com.gestor.financeiro.service;

import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service para gerenciamento de Refresh Tokens
 * 
 * Responsável por criar, validar, renovar e revogar tokens de atualização
 * 
 * @author Equipe de Desenvolvimento
 * @version 1.0
 * @since 2024-11-17
 */
@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // Tempo de expiração do refresh token (7 dias)
    private static final int EXPIRATION_DAYS = 7;

    /**
     * Cria um novo refresh token para um usuário
     * 
     * @param usuario o usuário para quem criar o token
     * @return o RefreshToken criado
     */
    @Transactional
    public RefreshToken criarRefreshToken(Usuario usuario) {
        // Gerar token único com UUID
        String tokenValue = UUID.randomUUID().toString();
        
        // Data de expiração (7 dias a partir de agora)
        LocalDateTime dataExpiracao = LocalDateTime.now().plusDays(EXPIRATION_DAYS);
        
        // Criar e salvar o token
        RefreshToken refreshToken = new RefreshToken(usuario, tokenValue, dataExpiracao);
        
        System.out.println(">>> DEBUG: Criando refresh token para usuário: " + usuario.getEmail());
        System.out.println(">>> DEBUG: Token expira em: " + dataExpiracao);
        
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Busca um refresh token pelo valor do token
     * 
     * @param token o valor do token
     * @return Optional contendo o RefreshToken se encontrado
     */
    public Optional<RefreshToken> buscarPorToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Valida um refresh token
     * Verifica se existe, não está expirado e não foi revogado
     * 
     * @param token o valor do token
     * @return o RefreshToken se válido
     * @throws RuntimeException se o token for inválido
     */
    public RefreshToken validarRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Refresh token não encontrado"));

        if (refreshToken.isExpirado()) {
            System.out.println(">>> DEBUG: Refresh token expirado!");
            throw new RuntimeException("Refresh token expirado");
        }

        if (refreshToken.getRevogado()) {
            System.out.println(">>> DEBUG: Refresh token revogado!");
            throw new RuntimeException("Refresh token revogado");
        }

        System.out.println(">>> DEBUG: Refresh token válido para usuário: " + refreshToken.getUsuario().getEmail());
        return refreshToken;
    }

    /**
     * Revoga um refresh token (usado no logout)
     * 
     * @param token o valor do token a ser revogado
     */
    @Transactional
    public void revogarToken(String token) {
        Optional<RefreshToken> refreshToken = refreshTokenRepository.findByToken(token);
        
        if (refreshToken.isPresent()) {
            RefreshToken rt = refreshToken.get();
            rt.revogar();
            refreshTokenRepository.save(rt);
            System.out.println(">>> DEBUG: Token revogado: " + token.substring(0, 20) + "...");
        }
    }

    /**
     * Revoga todos os tokens de um usuário (usado no logout de todos os dispositivos)
     * 
     * @param usuario o usuário
     */
    @Transactional
    public void revogarTodosTokensDoUsuario(Usuario usuario) {
        int count = refreshTokenRepository.revokeAllByUsuario(usuario);
        System.out.println(">>> DEBUG: " + count + " tokens revogados para usuário: " + usuario.getEmail());
    }

    /**
     * Deleta tokens expirados do banco (limpeza)
     * Pode ser chamado via @Scheduled task
     * 
     * @return número de tokens deletados
     */
    @Transactional
    public int limparTokensExpirados() {
        int count = refreshTokenRepository.deleteByDataExpiracaoBefore(LocalDateTime.now());
        System.out.println(">>> DEBUG: " + count + " tokens expirados deletados do banco");
        return count;
    }

    /**
     * Conta quantos tokens válidos um usuário possui
     * Útil para limitar número de dispositivos logados
     * 
     * @param usuario o usuário
     * @return número de tokens válidos
     */
    public long contarTokensValidosDoUsuario(Usuario usuario) {
        return refreshTokenRepository.countValidTokensByUsuario(usuario, LocalDateTime.now());
    }
}
