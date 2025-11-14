package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.LoginRequest;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.gestor.financeiro.dto.LoginResponse;
import org.springframework.http.ResponseEntity;
import java.util.Optional;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;  // Injeta o BCrypt

    @Transactional
    @PostMapping("/register")
    public Usuario register(@RequestBody Usuario usuario) {
        // Criptografa a senha ANTES de salvar
        String senhaCriptografada = passwordEncoder.encode(usuario.getSenha());
        usuario.setSenha(senhaCriptografada);
        
        // Salva no banco com senha criptografada
        return usuarioRepository.save(usuario);
    }

    @PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    // Busca usuário pelo email
    Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
    
    // Se não encontrou o usuário
    if (usuarioOpt.isEmpty()) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(new LoginResponse("Email ou senha incorretos", false));
    }
    
    Usuario usuario = usuarioOpt.get();
    
    // Verifica se a senha está correta
    if (passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
        return ResponseEntity
            .ok(new LoginResponse("Login realizado com sucesso!", true));
    } else {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(new LoginResponse("Email ou senha incorretos", false));
    }
}
}