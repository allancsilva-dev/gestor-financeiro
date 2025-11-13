package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// Indica que esta classe é um controller REST (API)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Injeta o repositório para acessar o banco
    @Autowired
    private UsuarioRepository usuarioRepository;

    // Endpoint para registrar um novo usuário
    @PostMapping("/register")
    public Usuario register(@RequestBody Usuario usuario) {
        // Salva o usuário no banco
        return usuarioRepository.save(usuario);
    }
}
