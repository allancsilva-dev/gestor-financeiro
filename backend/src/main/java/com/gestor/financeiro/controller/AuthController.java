package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.JwtUtil; 
import com.gestor.financeiro.dto.LoginRequest;
import com.gestor.financeiro.dto.LoginResponse;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired  // ← ADICIONE ESTAS 2 LINHAS!!!
    private JwtUtil jwtUtil;

    @Transactional
    @PostMapping("/register")
    public Usuario register(@RequestBody Usuario usuario) {
        String senhaCriptografada = passwordEncoder.encode(usuario.getSenha());
        usuario.setSenha(senhaCriptografada);
        return usuarioRepository.save(usuario);
    }

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
            // Gera o token JWT
            String token = jwtUtil.generateToken(usuario.getEmail());
                     
            return ResponseEntity
                .ok(new LoginResponse("Login realizado com sucesso!", true, token));
        } else {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse("Email ou senha incorretos", false));
        }
    }
}