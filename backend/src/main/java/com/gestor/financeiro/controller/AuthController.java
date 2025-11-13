package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
}