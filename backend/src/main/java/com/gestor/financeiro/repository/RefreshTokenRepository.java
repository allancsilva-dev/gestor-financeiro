package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository para RefreshToken
 * 
 * Gerencia operações de banco de dados para tokens de atualização
 * 
 * @author Equipe de Desenvolvimento
 * @version 1.0
 * @since 2024-11-17
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Busca um refresh token pelo valor do token
     * 
     * @param token o valor do token
     * @return Optional contendo o RefreshToken se encontrado
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Busca todos os refresh tokens de um usuário
     * 
     * @param usuario o usuário
     * @return lista de refresh tokens
     */
    List<RefreshToken> findByUsuario(Usuario usuario);

    /**
     * Busca refresh tokens válidos de um usuário (não expirados e não revogados)
     * 
     * @param usuario o usuário
     * @param agora data/hora atual para comparação
     * @return lista de refresh tokens válidos
     */
    @Query("SELECT rt FROM RefreshToken rt " +
           "WHERE rt.usuario = :usuario " +
           "AND rt.revogado = false " +
           "AND rt.dataExpiracao > :agora")
    List<RefreshToken> findValidTokensByUsuario(
        @Param("usuario") Usuario usuario,
        @Param("agora") LocalDateTime agora
    );

    /**
     * Deleta todos os tokens expirados
     * Útil para limpeza automática (pode ser executado via @Scheduled)
     * 
     * @param agora data/hora atual
     * @return número de tokens deletados
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.dataExpiracao < :agora")
    int deleteByDataExpiracaoBefore(@Param("agora") LocalDateTime agora);

    /**
     * Revoga todos os tokens de um usuário (usado no logout total)
     * 
     * @param usuario o usuário
     * @return número de tokens revogados
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revogado = true WHERE rt.usuario = :usuario")
    int revokeAllByUsuario(@Param("usuario") Usuario usuario);

    /**
     * Deleta todos os tokens de um usuário
     * 
     * @param usuario o usuário
     */
    void deleteByUsuario(Usuario usuario);

    /**
     * Conta quantos tokens válidos um usuário possui
     * 
     * @param usuario o usuário
     * @param agora data/hora atual
     * @return número de tokens válidos
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt " +
           "WHERE rt.usuario = :usuario " +
           "AND rt.revogado = false " +
           "AND rt.dataExpiracao > :agora")
    long countValidTokensByUsuario(
        @Param("usuario") Usuario usuario,
        @Param("agora") LocalDateTime agora
    );
}
