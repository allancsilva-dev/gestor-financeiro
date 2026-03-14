package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.UsuarioResponseDto;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.security.AuthenticatedUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDto> getCurrentUser() {
        Usuario usuarioAutenticado = authenticatedUserService.getAuthenticatedUser();
        String email = usuarioAutenticado.getEmail();
        
        // Busca o usuário no banco
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        return ResponseEntity.ok(UsuarioResponseDto.fromEntity(usuario));
    }
}