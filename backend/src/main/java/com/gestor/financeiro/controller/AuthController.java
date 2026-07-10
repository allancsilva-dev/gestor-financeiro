package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.JwtUtil;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.exception.AccountLockedException;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.PasswordResetToken;
import com.gestor.financeiro.model.RefreshToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.PasswordResetTokenRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.EmailService;
import com.gestor.financeiro.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
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
@Tag(name = "Autenticação", description = "Fluxos de login, registro, sessão e recuperação de senha")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String CSRF_COOKIE_NAME = "csrfToken";
    private static final String MOBILE_CLIENT_HEADER = "X-Client-Type";
    private static final String MOBILE_CLIENT_VALUE = "mobile";

    private static final long REFRESH_COOKIE_MAX_AGE_SECONDS = 7L * 24 * 3600;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    @org.springframework.beans.factory.annotation.Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${security.auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.auth.lockout-minutes:15}")
    private int lockoutMinutes;

    // ==========================================
    // REGISTRO
    // ==========================================
    
    @Transactional
    @PostMapping("/register")
    @Operation(summary = "Registrar usuário", description = "Cria uma nova conta de usuário")
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

    // Nunca expor a entidade (contém hash de senha e campos de lockout)
    return ResponseEntity.ok(Map.of(
        "id", usuarioSalvo.getId(),
        "nome", usuarioSalvo.getNome(),
        "email", usuarioSalvo.getEmail()
    ));
}

    @PostMapping("/login")
    @Operation(summary = "Realizar login", description = "Autentica e retorna access token com refresh token em cookie HttpOnly")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
        
        if (usuarioOpt.isEmpty()) {
            throw new BusinessException("Email ou senha incorretos");
        }
        
        Usuario usuario = usuarioOpt.get();

        if (isAccountLocked(usuario)) {
            long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), usuario.getLockedUntil()).getSeconds();
            throw new AccountLockedException(
                String.format("Conta temporariamente bloqueada. Tente novamente em %d segundos.", Math.max(0, remainingSeconds))
            );
        }

        if (passwordEncoder.matches(request.getPassword(), usuario.getSenha())) {
            resetFailedAttempts(usuario);

            String accessToken = jwtUtil.generateToken(usuario.getEmail());

            RefreshToken refreshToken = refreshTokenService.criarRefreshToken(usuario);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login realizado com sucesso!");
            response.put("success", true);
            response.put("accessToken", accessToken);
            response.put("usuario", Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "onboardingCompleto", usuario.isOnboardingCompleto()
            ));

            // csrfToken também no body: clientes nativos (React Native) não conseguem
            // ler cookies para o double-submit; body cross-origin continua ilegível no browser
            String csrfToken = createCsrfToken();
            response.put("csrfToken", csrfToken);
            if (isMobileClient(servletRequest)) {
                response.put("refreshToken", refreshToken.getToken());
            }

            ResponseCookie refreshCookie = buildRefreshTokenCookie(refreshToken.getToken(), REFRESH_COOKIE_MAX_AGE_SECONDS);
            ResponseCookie csrfCookie = buildCsrfCookie(csrfToken, REFRESH_COOKIE_MAX_AGE_SECONDS);

            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), csrfCookie.toString())
                .body(response);
        } else {
            incrementFailedAttempts(usuario);
            throw new BusinessException("Email ou senha incorretos");
        }
    }

    /**
     * Renova o access token usando o refresh token
     * 
     * POST /api/auth/refresh-token
     * Cookie HttpOnly: refreshToken
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "Renovar access token", description = "Gera novo access token com rotação de refresh token")
    public ResponseEntity<?> refreshToken(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshTokenRequest body
    ) {
        String refreshTokenValue = extractRefreshTokenFromCookies(request);
        if ((refreshTokenValue == null || refreshTokenValue.isEmpty()) && body != null) {
            refreshTokenValue = body.refreshToken();
        }
        String clientIp = extractClientIp(request);

        if (refreshTokenValue == null || refreshTokenValue.isEmpty()) {
            throw new BusinessException("Refresh token não fornecido");
        }

        // Rotaciona token: revoga atual e emite um novo.
        RefreshToken refreshToken = refreshTokenService.rotacionarRefreshToken(refreshTokenValue, clientIp);

        // Gerar novo access token
        String novoAccessToken = jwtUtil.generateToken(refreshToken.getUsuario().getEmail());

        // Resposta
        String csrfToken = createCsrfToken();
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", novoAccessToken);
        response.put("csrfToken", csrfToken);
        if (isMobileClient(request)) {
            response.put("refreshToken", refreshToken.getToken());
        }

        ResponseCookie refreshCookie = buildRefreshTokenCookie(refreshToken.getToken(), REFRESH_COOKIE_MAX_AGE_SECONDS);
        ResponseCookie csrfCookie = buildCsrfCookie(csrfToken, REFRESH_COOKIE_MAX_AGE_SECONDS);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), csrfCookie.toString())
            .body(response);
    }

    /**
     * Logout com revogação de refresh token
     * 
     * POST /api/auth/logout
     * Cookie HttpOnly: refreshToken
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout atual", description = "Revoga refresh token da sessão atual")
    public ResponseEntity<?> logout(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshTokenRequest body
    ) {
        String refreshToken = extractRefreshTokenFromCookies(request);
        if ((refreshToken == null || refreshToken.isEmpty()) && body != null) {
            refreshToken = body.refreshToken();
        }

        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenService.revogarToken(refreshToken);
        }

        ResponseCookie clearCookie = buildRefreshTokenCookie("", 0);
        ResponseCookie clearCsrfCookie = buildCsrfCookie("", 0);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearCookie.toString(), clearCsrfCookie.toString())
            .body(Map.of("message", "Logout realizado com sucesso"));
    }

    /**
     * Logout de todos os dispositivos
     * 
     * POST /api/auth/logout-all
     * Headers: Authorization: Bearer {token}
     */
    @PostMapping("/logout-all")
    @Operation(summary = "Logout global", description = "Revoga todos os refresh tokens do usuário")
    public ResponseEntity<?> logoutAll(Authentication authentication) {
        String email = authentication.getName();
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        refreshTokenService.revogarTodosTokensDoUsuario(usuario);
        ResponseCookie clearCookie = buildRefreshTokenCookie("", 0);
        ResponseCookie clearCsrfCookie = buildCsrfCookie("", 0);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearCookie.toString(), clearCsrfCookie.toString())
            .body(Map.of("message", "Logout realizado em todos os dispositivos"));
    }

    @Transactional
    @PostMapping("/forgot-password")
    @Operation(summary = "Solicitar recuperação de senha", description = "Gera token temporário para reset de senha")
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
    @Operation(summary = "Resetar senha", description = "Aplica nova senha com token válido de recuperação")
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
    @Operation(summary = "Validar token de recuperação", description = "Confere validade do token de reset de senha")
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

    private boolean isAccountLocked(Usuario usuario) {
        if (usuario.getLockedUntil() == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(usuario.getLockedUntil())) {
            usuario.setLockedUntil(null);
            usuarioRepository.save(usuario);
            return false;
        }
        return true;
    }

    private void resetFailedAttempts(Usuario usuario) {
        usuario.setFailedAttempts(0);
        usuario.setLockedUntil(null);
        usuarioRepository.save(usuario);
    }

    private void incrementFailedAttempts(Usuario usuario) {
        usuario.setFailedAttempts(usuario.getFailedAttempts() + 1);
        if (usuario.getFailedAttempts() >= maxFailedAttempts) {
            usuario.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }
        usuarioRepository.save(usuario);
    }

    private ResponseCookie buildRefreshTokenCookie(String tokenValue, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, tokenValue)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/api/auth")
            .sameSite("Lax")
            .maxAge(maxAgeSeconds)
            .build();
    }

    private ResponseCookie buildCsrfCookie(String tokenValue, long maxAgeSeconds) {
        return ResponseCookie.from(CSRF_COOKIE_NAME, tokenValue)
            .httpOnly(false)
            .secure(cookieSecure)
            .path("/api/auth")
            .sameSite("Lax")
            .maxAge(maxAgeSeconds)
            .build();
    }

    private String createCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isMobileClient(HttpServletRequest request) {
        return MOBILE_CLIENT_VALUE.equalsIgnoreCase(request.getHeader(MOBILE_CLIENT_HEADER));
    }

    private String extractRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
