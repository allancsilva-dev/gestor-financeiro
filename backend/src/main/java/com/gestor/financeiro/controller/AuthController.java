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
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // POST /api/auth/register - Cadastra novo usuário
    @Transactional
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Usuario usuario) {
        // Verifica se email já existe
        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(usuario.getEmail());
        if (usuarioExistente.isPresent()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Email já cadastrado!");
        }
        
        // Criptografa a senha
        String senhaCriptografada = passwordEncoder.encode(usuario.getSenha());
        usuario.setSenha(senhaCriptografada);
        
        // Salva no banco
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