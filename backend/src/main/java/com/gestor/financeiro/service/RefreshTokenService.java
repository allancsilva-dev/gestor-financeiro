package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.exception.TokenReuseDetectedException;
import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

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
            .orElseThrow(() -> new ResourceNotFoundException("Refresh token não encontrado"));

        if (refreshToken.isExpirado()) {
            throw new BusinessException("Refresh token expirado");
        }

        if (refreshToken.getRevogado()) {
            throw new BusinessException("Refresh token revogado");
        }

        return refreshToken;
    }

    /**
     * Rotaciona refresh token válido (revoga o antigo e cria um novo para o mesmo usuário).
     *
     * @param tokenAtual o refresh token atual
     * @return novo refresh token persistido
     */
    @Transactional
    public RefreshToken rotacionarRefreshToken(String tokenAtual, String clientIp) {
        RefreshToken atual = refreshTokenRepository.findByToken(tokenAtual)
            .orElseThrow(() -> new ResourceNotFoundException("Refresh token não encontrado"));

        if (atual.isExpirado()) {
            throw new BusinessException("Refresh token expirado");
        }

        if (atual.getRevogado()) {
            Long userId = atual.getUsuario() != null ? atual.getUsuario().getId() : null;
            if (atual.getUsuario() != null) {
                refreshTokenRepository.revokeAllByUsuario(atual.getUsuario());
            }

            log.warn("SECURITY: Refresh token reuse detected. userId={}, ip={}", userId, clientIp);
            throw new TokenReuseDetectedException("Sessão invalidada por segurança. Faça login novamente.");
        }

        atual.revogar();
        refreshTokenRepository.save(atual);

        RefreshToken novo = criarRefreshToken(atual.getUsuario());
        return novo;
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
        }
    }

    /**
     * Revoga todos os tokens de um usuário (usado no logout de todos os dispositivos)
     * 
     * @param usuario o usuário
     */
    @Transactional
    public void revogarTodosTokensDoUsuario(Usuario usuario) {
        refreshTokenRepository.revokeAllByUsuario(usuario);
    }

    /**
     * Deleta tokens expirados do banco (limpeza)
     * Pode ser chamado via @Scheduled task
     * 
     * @return número de tokens deletados
     */
    @Transactional
    public int limparTokensExpirados() {
        return refreshTokenRepository.deleteByDataExpiracaoBefore(LocalDateTime.now());
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
