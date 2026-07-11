package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.ApiError;
import com.gestor.financeiro.service.RateLimitService;
import com.gestor.financeiro.service.RateLimitService.RateLimitDecision;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final int LOGIN_LIMIT = 5;

    private static final int FORGOT_PASSWORD_LIMIT = 3;

    private static final int REGISTER_LIMIT = 5;

    private static final int RESET_PASSWORD_LIMIT = 5;

    private static final int VALIDATE_TOKEN_LIMIT = 10;

    private static final String LOGIN_PATH = "/api/auth/login";

    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";

    private static final String REGISTER_PATH = "/api/auth/register";

    private static final String RESET_PASSWORD_PATH = "/api/auth/reset-password";

    private static final String VALIDATE_TOKEN_PATH = "/api/auth/validate-token";

    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    public LoginRateLimitFilter(ObjectMapper objectMapper, RateLimitService rateLimitService) {
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!isRateLimitedMethod(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = resolveLimitForPath(path);

        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = buildClientKey(request, path);
        RateLimitDecision decision = rateLimitService.consume(key, limit, WINDOW);
        if (!decision.allowed()) {
            writeRateLimitResponse(response, limit, decision.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimitForPath(String path) {
        if (path == null) {
            return -1;
        }

        if (path.equals(LOGIN_PATH)) {
            return LOGIN_LIMIT;
        }

        if (path.equals(FORGOT_PASSWORD_PATH)) {
            return FORGOT_PASSWORD_LIMIT;
        }

        if (path.equals(REGISTER_PATH)) {
            return REGISTER_LIMIT;
        }

        if (path.equals(RESET_PASSWORD_PATH)) {
            return RESET_PASSWORD_LIMIT;
        }

        if (path.equals(VALIDATE_TOKEN_PATH)) {
            return VALIDATE_TOKEN_LIMIT;
        }

        return -1;
    }

    private boolean isRateLimitedMethod(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            return true;
        }

        return "GET".equalsIgnoreCase(method) && VALIDATE_TOKEN_PATH.equals(path);
    }

    private String buildClientKey(HttpServletRequest request, String path) {
        // Não ler X-Forwarded-For cru: header é spoofável por qualquer cliente.
        // Atrás de proxy, server.forward-headers-strategy=framework já faz o
        // ForwardedHeaderFilter refletir o IP real em getRemoteAddr().
        return path + "|" + request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, int limit, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", "0");

        ApiError apiError = new ApiError(
            "RATE_LIMIT",
            "Muitas tentativas. Aguarde 60 segundos e tente novamente.",
            Instant.now(),
            Map.of()
        );

        objectMapper.writeValue(response.getWriter(), apiError);
    }
}
