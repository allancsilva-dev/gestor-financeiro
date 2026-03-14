package com.gestor.financeiro.security;

import com.gestor.financeiro.exception.UnauthorizedAccessException;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Long getAuthenticatedUserId() {
        return getAuthenticatedUser().getId();
    }

    public Usuario getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedAccessException("Usuário não autenticado");
        }

        String email = authentication.getName();
        return usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new UnauthorizedAccessException("Usuário autenticado não encontrado"));
    }
}
