package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.JwtUtil;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.model.PasswordResetToken;
import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.PasswordResetTokenRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.EmailService;
import com.gestor.financeiro.service.RefreshTokenService;
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
@CrossOrigin(origins = "*")
public class AuthController {

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
    public ResponseEntity<?> register(@RequestBody Usuario usuario) {
        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(usuario.getEmail());
        if (usuarioExistente.isPresent()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Email já cadastrado!");
        }
        
        String senhaCriptografada = passwordEncoder.encode(usuario.getSenha());
        usuario.setSenha(senhaCriptografada);
        
        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        
        return ResponseEntity.ok(usuarioSalvo);
    }

    // ==========================================
    // LOGIN (ATUALIZADO COM REFRESH TOKEN)
    // ==========================================
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
        
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Email ou senha incorretos", false));
        }
        
        Usuario usuario = usuarioOpt.get();
        
        if (passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
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
            
            System.out.println(">>> DEBUG: Login realizado com refresh token para: " + usuario.getEmail());
            
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Email ou senha incorretos", false));
        }
    }

    // ==========================================
    // REFRESH TOKEN (NOVO)
    // ==========================================
    
    /**
     * Renova o access token usando o refresh token
     * 
     * POST /api/auth/refresh-token
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshTokenValue = request.get("refreshToken");
            
            if (refreshTokenValue == null || refreshTokenValue.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Refresh token não fornecido"
                ));
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

            System.out.println(">>> DEBUG: Access token renovado para: " + refreshToken.getUsuario().getEmail());

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    // ==========================================
    // LOGOUT (ATUALIZADO COM REFRESH TOKEN)
    // ==========================================
    
    /**
     * Logout com revogação de refresh token
     * 
     * POST /api/auth/logout
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            
            if (refreshToken != null && !refreshToken.isEmpty()) {
                refreshTokenService.revogarToken(refreshToken);
                System.out.println(">>> DEBUG: Refresh token revogado no logout");
            }

            return ResponseEntity.ok(Map.of(
                "message", "Logout realizado com sucesso"
            ));
            
        } catch (Exception e) {
            // Mesmo com erro, retorna sucesso (logout sempre funciona)
            return ResponseEntity.ok(Map.of(
                "message", "Logout realizado"
            ));
        }
    }

    /**
     * Logout de todos os dispositivos
     * 
     * POST /api/auth/logout-all
     * Headers: Authorization: Bearer {token}
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(Authentication authentication) {
        try {
            String email = authentication.getName();
            Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            refreshTokenService.revogarTodosTokensDoUsuario(usuario);
            
            System.out.println(">>> DEBUG: Todos os tokens revogados para: " + email);

            return ResponseEntity.ok(Map.of(
                "message", "Logout realizado em todos os dispositivos"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    // ==========================================
    // RECUPERAÇÃO DE SENHA (MANTIDO)
    // ==========================================
    
    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
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
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(request.getToken());
        
        if (tokenOpt.isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token inválido!");
        }
        
        PasswordResetToken resetToken = tokenOpt.get();
        
        if (resetToken.isExpired()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token expirado! Solicite um novo link de recuperação.");
        }
        
        if (resetToken.getUsado()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token já foi utilizado!");
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
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token inválido!");
        }
        
        PasswordResetToken resetToken = tokenOpt.get();
        
        if (resetToken.isExpired()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token expirado!");
        }
        
        if (resetToken.getUsado()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token já utilizado!");
        }
        
        return ResponseEntity.ok("Token válido!");
    }
}