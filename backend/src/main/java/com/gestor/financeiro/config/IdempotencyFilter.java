package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Order(2)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String ATTRIBUTE = "idempotencyKey";
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (!VALID_KEY.matcher(key).matches()) {
            writeInvalidKey(response, request);
            return;
        }

        request.setAttribute(ATTRIBUTE, key);
        chain.doFilter(request, response);
    }

    private void writeInvalidKey(HttpServletResponse response, HttpServletRequest request) throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        ApiError error = new ApiError(
                "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key deve conter de 1 a 100 caracteres alfanuméricos ou . _ : -",
                Instant.now(),
                requestId != null ? requestId.toString() : null,
                null
        );
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
