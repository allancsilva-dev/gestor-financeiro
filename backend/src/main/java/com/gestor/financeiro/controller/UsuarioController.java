package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.AlterarSenhaRequest;
import com.gestor.financeiro.dto.UsuarioUpdateRequest;
import com.gestor.financeiro.dto.UsuarioResponseDto;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.security.AuthenticatedUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuários", description = "Dados do usuário autenticado")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDto> getCurrentUser() {
        Usuario usuarioAutenticado = authenticatedUserService.getAuthenticatedUser();
        String email = usuarioAutenticado.getEmail();
        
        // Busca o usuário no banco
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        
        return ResponseEntity.ok(UsuarioResponseDto.fromEntity(usuario));
    }

    @PutMapping("/me")
    public ResponseEntity<UsuarioResponseDto> atualizarPerfil(@Valid @RequestBody UsuarioUpdateRequest request) {
        Usuario usuario = usuarioRepository.findById(authenticatedUserService.getAuthenticatedUserId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        usuario.setNome(request.getNome().trim());
        return ResponseEntity.ok(UsuarioResponseDto.fromEntity(usuarioRepository.save(usuario)));
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> alterarSenha(@Valid @RequestBody AlterarSenhaRequest request) {
        Usuario usuario = usuarioRepository.findById(authenticatedUserService.getAuthenticatedUserId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new BusinessException("Senha atual incorreta");
        }

        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuarioRepository.save(usuario);
        return ResponseEntity.noContent().build();
    }
}
