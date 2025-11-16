package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.JwtUtil;
import com.gestor.financeiro.dto.*;
import com.gestor.financeiro.model.PasswordResetToken;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.PasswordResetTokenRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

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

    // POST /api/auth/register - Cadastra novo usuário
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

    // POST /api/auth/login - Faz login
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
            String token = jwtUtil.generateToken(usuario.getEmail());
            return ResponseEntity.ok(new LoginResponse("Login realizado com sucesso!", true, token));
        } else {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Email ou senha incorretos", false));
        }
    }

    // POST /api/auth/forgot-password - Solicita recuperação de senha
    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
        
        if (usuarioOpt.isEmpty()) {
            // Por segurança, não revelamos se o email existe ou não
            return ResponseEntity.ok("Se o email existir, você receberá um link de recuperação.");
        }
        
        Usuario usuario = usuarioOpt.get();
        
        // Remove tokens antigos deste usuário
        tokenRepository.findByUsuario(usuario).ifPresent(tokenRepository::delete);
        
        // Gera novo token único
        String token = UUID.randomUUID().toString();
        
        // Salva o token
        PasswordResetToken resetToken = new PasswordResetToken(token, usuario);
        tokenRepository.save(resetToken);
        
        // Envia email (por enquanto só imprime no console)
        emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), token);
        
        return ResponseEntity.ok("Se o email existir, você receberá um link de recuperação.");
    }

    // POST /api/auth/reset-password - Redefine a senha
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
        
        // Verifica se o token expirou
        if (resetToken.isExpired()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token expirado! Solicite um novo link de recuperação.");
        }
        
        // Verifica se já foi usado
        if (resetToken.getUsado()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Token já foi utilizado!");
        }
        
        // Atualiza a senha
        Usuario usuario = resetToken.getUsuario();
        String novaSenhaCriptografada = passwordEncoder.encode(request.getNovaSenha());
        usuario.setSenha(novaSenhaCriptografada);
        usuarioRepository.save(usuario);
        
        // Marca o token como usado
        resetToken.setUsado(true);
        tokenRepository.save(resetToken);
        
        return ResponseEntity.ok("Senha alterada com sucesso!");
    }

    // GET /api/auth/validate-token - Valida se o token é válido
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