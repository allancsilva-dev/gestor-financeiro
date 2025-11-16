package com.gestor.financeiro.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        
        System.out.println("========================================");
        System.out.println("🔍 JWT FILTER - URL: " + requestPath);
        
        // ✅ PULA O FILTRO PARA ROTAS PÚBLICAS
        if (requestPath.startsWith("/api/auth/")) {
            System.out.println("✅ ROTA PÚBLICA - Pulando autenticação JWT");
            System.out.println("========================================");
            filterChain.doFilter(request, response);
            return;  // ← IMPORTANTE: Para aqui!
        }
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;
        
        System.out.println("🔍 Authorization Header: " + authHeader);
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            System.out.println("✅ Token encontrado: " + token.substring(0, Math.min(20, token.length())) + "...");
            
            try {
                email = jwtUtil.extractEmail(token);
                System.out.println("✅ Email extraído: " + email);
            } catch (Exception e) {
                System.out.println("❌ ERRO ao extrair email: " + e.getMessage());
            }
        } else {
            System.out.println("❌ Header Authorization inválido ou não encontrado");
        }
        
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            System.out.println("🔍 Buscando usuário no banco: " + email);
            
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                System.out.println("✅ Usuário encontrado: " + userDetails.getUsername());
                
                if (jwtUtil.validateToken(token, email)) {
                    System.out.println("✅ Token VÁLIDO!");
                    
                    UsernamePasswordAuthenticationToken authToken = 
                        new UsernamePasswordAuthenticationToken(
                            userDetails, 
                            null, 
                            userDetails.getAuthorities()
                        );
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    System.out.println("✅ Usuário AUTENTICADO com sucesso!");
                } else {
                    System.out.println("❌ Token INVÁLIDO!");
                }
            } catch (Exception e) {
                System.out.println("❌ ERRO ao buscar usuário: " + e.getMessage());
            }
        } else {
            if (email == null) {
                System.out.println("⚠️ Email é NULL - não vai autenticar");
            }
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                System.out.println("⚠️ Usuário já está autenticado");
            }
        }
        
        System.out.println("========================================");
        
        filterChain.doFilter(request, response);
    }
}