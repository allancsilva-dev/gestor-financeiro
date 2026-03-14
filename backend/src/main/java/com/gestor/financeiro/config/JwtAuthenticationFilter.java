package com.gestor.financeiro.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        log.debug("JWT filter interceptando URL {}", requestPath);
        
        // ✅ PULA O FILTRO PARA ROTAS PÚBLICAS
        if (requestPath.startsWith("/api/auth/")) {
            log.debug("Rota pública de auth detectada, pulando autenticação JWT");
            filterChain.doFilter(request, response);
            return;  // ← IMPORTANTE: Para aqui!
        }
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;
        
        log.debug("Authorization header recebido");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            log.debug("Token Bearer encontrado no header");
            
            try {
                email = jwtUtil.extractEmail(token);
                log.debug("Claims básicas do token extraídas com sucesso");
            } catch (Exception e) {
                // Evita despejar stacktrace para erros esperados de token inválido.
                log.warn("Token JWT inválido ou malformado: {}", e.getClass().getSimpleName());
            }
        } else {
            log.debug("Header Authorization inválido ou ausente");
        }
        
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("Buscando usuário no UserDetailsService para concluir autenticação JWT");
            
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                log.debug("Usuário encontrado para autenticação JWT");
                
                if (jwtUtil.validateToken(token, email)) {
                    log.debug("Token JWT válido");
                    
                    UsernamePasswordAuthenticationToken authToken = 
                        new UsernamePasswordAuthenticationToken(
                            userDetails, 
                            null, 
                            userDetails.getAuthorities()
                        );
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("Usuário autenticado com sucesso via JWT");
                } else {
                    log.warn("Token JWT inválido após validação de assinatura/expiração");
                }
            } catch (Exception e) {
                log.warn("Falha ao autenticar via JWT: {}", e.getClass().getSimpleName());
            }
        } else {
            if (email == null) {
                log.debug("Email ausente após leitura do token; autenticação não será aplicada");
            }
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.debug("Usuário já autenticado no contexto");
            }
        }
        
        filterChain.doFilter(request, response);
    }
}