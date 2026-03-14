package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;

    private static final int LOGIN_LIMIT = 5;

    private static final int FORGOT_PASSWORD_LIMIT = 3;

    private static final String LOGIN_PATH = "/api/auth/login";

    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";

    private final ConcurrentHashMap<String, List<Long>> attemptsByKey = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        int limit = resolveLimitForPath(path);

        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = buildClientKey(request, path);
        long now = System.currentTimeMillis();

        List<Long> timestamps = attemptsByKey.computeIfAbsent(key, unused -> new ArrayList<>());

        synchronized (timestamps) {
            // Remove tentativas fora da janela móvel de 1 minuto.
            timestamps.removeIf(ts -> (now - ts) > WINDOW_MILLIS);

            if (timestamps.size() >= limit) {
                writeRateLimitResponse(response, limit);
                return;
            }

            timestamps.add(now);
        }

        cleanupIfEmptyOldEntry(key);
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

        return -1;
    }

    private String buildClientKey(HttpServletRequest request, String path) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip;

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            ip = forwardedFor.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }

        return path + "|" + ip;
    }

    private void writeRateLimitResponse(HttpServletResponse response, int limit) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        response.setHeader("Retry-After", "60");
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

    private void cleanupIfEmptyOldEntry(String key) {
        List<Long> timestamps = attemptsByKey.get(key);
        if (timestamps == null) {
            return;
        }

        synchronized (timestamps) {
            if (timestamps.isEmpty()) {
                attemptsByKey.remove(key, timestamps);
            }
        }
    }
}
