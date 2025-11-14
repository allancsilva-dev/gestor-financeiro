package com.gestor.financeiro.controller;

import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        // Pega a autenticação do contexto
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Verifica se está autenticado
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Token inválido ou ausente");
        }
        
        // Pega o email do usuário autenticado
        String email = authentication.getName();
        
        // Busca o usuário no banco
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        // Retorna os dados (SEM a senha!)
        usuario.setSenha(null);
        return ResponseEntity.ok(usuario);
    }
}