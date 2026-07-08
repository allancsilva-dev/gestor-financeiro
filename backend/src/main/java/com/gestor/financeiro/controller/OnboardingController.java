package com.gestor.financeiro.controller;

import com.gestor.financeiro.dto.OnboardingStatusResponse;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.security.AuthenticatedUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Fluxo de onboarding financeiro guiado")
public class OnboardingController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @GetMapping("/status")
    @Operation(summary = "Verifica se onboarding está completo")
    public ResponseEntity<OnboardingStatusResponse> getStatus() {
        Usuario usuario = authenticatedUserService.getAuthenticatedUser();
        Usuario usuarioAtualizado = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return ResponseEntity.ok(new OnboardingStatusResponse(usuarioAtualizado.isOnboardingCompleto()));
    }

    @PostMapping("/completar")
    @Transactional
    @Operation(summary = "Marca onboarding como concluído")
    public ResponseEntity<OnboardingStatusResponse> completar() {
        Usuario usuario = authenticatedUserService.getAuthenticatedUser();
        Usuario usuarioAtualizado = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        usuarioAtualizado.setOnboardingCompleto(true);
        usuarioRepository.save(usuarioAtualizado);
        return ResponseEntity.ok(new OnboardingStatusResponse(true));
    }
}
