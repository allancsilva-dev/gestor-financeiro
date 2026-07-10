package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class RefreshTokenCsrfFilter extends OncePerRequestFilter {

    public static final String CSRF_COOKIE_NAME = "csrfToken";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_PATH = "/api/auth/refresh-token";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String MOBILE_CLIENT_HEADER = "X-Client-Type";
    private static final String MOBILE_CLIENT_VALUE = "mobile";

    private final ObjectMapper objectMapper;

    public RefreshTokenCsrfFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!requiresCsrf(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isMobileClient(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (refreshToken == null || refreshToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String csrfCookie = extractCookie(request, CSRF_COOKIE_NAME);
        String csrfHeader = request.getHeader(CSRF_HEADER_NAME);

        if (csrfCookie == null || csrfHeader == null || !csrfCookie.equals(csrfHeader)) {
            writeForbidden(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresCsrf(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        return REFRESH_TOKEN_PATH.equals(path) || LOGOUT_PATH.equals(path);
    }

    private boolean isMobileClient(HttpServletRequest request) {
        return MOBILE_CLIENT_VALUE.equalsIgnoreCase(request.getHeader(MOBILE_CLIENT_HEADER));
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private void writeForbidden(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        ApiError apiError = new ApiError(
            "CSRF_REQUIRED",
            "CSRF token ausente ou inválido",
            Instant.now(),
            requestId != null ? requestId.toString() : null,
            Map.of()
        );

        objectMapper.writeValue(response.getWriter(), apiError);
    }
}
