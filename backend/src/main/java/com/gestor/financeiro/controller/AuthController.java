package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.JwtUtil;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.PasswordResetToken;
import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.PasswordResetTokenRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.EmailService;
import com.gestor.financeiro.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller de Autenticação COMPLETO
 * 
 * Funcionalidades:
 * - Registro e Login com Refresh Token
 * - Recuperação de Senha
 * - Renovação de Token
 * - Logout
 * 
 * @author Equipe de Desenvolvimento
 * @version 3.0
 * @since 2024-11-17
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    // ==========================================
    // REGISTRO
    // ==========================================
    
    @Transactional
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(request.getEmail());
        if (usuarioExistente.isPresent()) {
            throw new BusinessException("Email já cadastrado!");
        }
    
    Usuario usuario = new Usuario();
    usuario.setNome(request.getNome());
    usuario.setEmail(request.getEmail());
    usuario.setSenha(passwordEncoder.encode(request.getPassword()));
    
    Usuario usuarioSalvo = usuarioRepository.save(usuario);
    
    return ResponseEntity.ok(usuarioSalvo);
}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
        
        if (usuarioOpt.isEmpty()) {
            throw new BusinessException("Email ou senha incorretos");
        }
        
        Usuario usuario = usuarioOpt.get();
        
        if (passwordEncoder.matches(request.getPassword(), usuario.getSenha())) {
            // Gerar access token (15 minutos)
            String accessToken = jwtUtil.generateToken(usuario.getEmail());
            
            // Gerar refresh token (7 dias)
            RefreshToken refreshToken = refreshTokenService.criarRefreshToken(usuario);
            
            // Montar resposta com AMBOS os tokens
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login realizado com sucesso!");
            response.put("success", true);
            response.put("token", accessToken); // Mantém compatibilidade com frontend antigo
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken.getToken());
            response.put("usuario", Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail()
            ));
            
            log.info("Login realizado com refresh token para usuário {}", usuario.getEmail());
            
            return ResponseEntity.ok(response);
        } else {
            throw new BusinessException("Email ou senha incorretos");
        }
    }

    /**
     * Renova o access token usando o refresh token
     * 
     * POST /api/auth/refresh-token
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody Map<String, String> request) {
        String refreshTokenValue = request.get("refreshToken");

        if (refreshTokenValue == null || refreshTokenValue.isEmpty()) {
            throw new BusinessException("Refresh token não fornecido");
        }

        // Validar refresh token
        RefreshToken refreshToken = refreshTokenService.validarRefreshToken(refreshTokenValue);

        // Gerar novo access token
        String novoAccessToken = jwtUtil.generateToken(refreshToken.getUsuario().getEmail());

        // Resposta
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", novoAccessToken);
        response.put("token", novoAccessToken); // Compatibilidade
        response.put("refreshToken", refreshTokenValue);

        log.info("Access token renovado para usuário {}", refreshToken.getUsuario().getEmail());

        return ResponseEntity.ok(response);
    }

    /**
     * Logout com revogação de refresh token
     * 
     * POST /api/auth/logout
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenService.revogarToken(refreshToken);
            log.info("Refresh token revogado no logout");
        }

        return ResponseEntity.ok(Map.of(
            "message", "Logout realizado com sucesso"
        ));
    }

    /**
     * Logout de todos os dispositivos
     * 
     * POST /api/auth/logout-all
     * Headers: Authorization: Bearer {token}
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        refreshTokenService.revogarTodosTokensDoUsuario(usuario);
        log.info("Todos os tokens revogados para usuário {}", email);

        return ResponseEntity.ok(Map.of(
            "message", "Logout realizado em todos os dispositivos"
        ));
    }

    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
        
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.ok("Se o email existir, você receberá um link de recuperação.");
        }
        
        Usuario usuario = usuarioOpt.get();
        
        tokenRepository.findByUsuario(usuario).ifPresent(tokenRepository::delete);
        
        String token = UUID.randomUUID().toString();
        
        PasswordResetToken resetToken = new PasswordResetToken(token, usuario);
        tokenRepository.save(resetToken);
        
        emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), token);
        
        return ResponseEntity.ok("Se o email existir, você receberá um link de recuperação.");
    }

    @Transactional
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(request.getToken());
        
        if (tokenOpt.isEmpty()) {
            throw new BusinessException("Token inválido!");
        }
        
        PasswordResetToken resetToken = tokenOpt.get();
        
        if (resetToken.isExpired()) {
            throw new BusinessException("Token expirado! Solicite um novo link de recuperação.");
        }
        
        if (resetToken.getUsado()) {
            throw new BusinessException("Token já foi utilizado!");
        }
        
        Usuario usuario = resetToken.getUsuario();
        String novaSenhaCriptografada = passwordEncoder.encode(request.getNovaSenha());
        usuario.setSenha(novaSenhaCriptografada);
        usuarioRepository.save(usuario);
        
        resetToken.setUsado(true);
        tokenRepository.save(resetToken);
        
        return ResponseEntity.ok("Senha alterada com sucesso!");
    }

    @GetMapping("/validate-token")
    public ResponseEntity<?> validateToken(@RequestParam String token) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        
        if (tokenOpt.isEmpty()) {
            throw new BusinessException("Token inválido!");
        }
        
        PasswordResetToken resetToken = tokenOpt.get();
        
        if (resetToken.isExpired()) {
            throw new BusinessException("Token expirado!");
        }
        
        if (resetToken.getUsado()) {
            throw new BusinessException("Token já utilizado!");
        }
        
        return ResponseEntity.ok("Token válido!");
    }
}