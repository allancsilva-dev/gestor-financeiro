package com.gestor.financeiro.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Simula respostas perdidas somente no E2E local, sempre depois do commit da operação. */
@Component
@Profile("local-e2e")
@Order(3)
public class LocalE2eAssistantFaultFilter extends OncePerRequestFilter {
    private static final String RETRY_MARKER = "e2e retry mercado 50 hoje";
    private static final String CONFIRM_RETRY_MARKER = "e2e confirm retry mercado 50 hoje";
    private static final Pattern CONFIRM_PATH = Pattern.compile("/api/v1/assistant/drafts/(\\d+)/confirm");

    private final ObjectMapper mapper;
    private final Set<String> droppedMessageKeys = ConcurrentHashMap.newKeySet();
    private final Set<Long> armedConfirmationDrafts = ConcurrentHashMap.newKeySet();

    public LocalE2eAssistantFaultFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/assistant/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(cachedRequest, cachedResponse);

        String body = new String(cachedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
        boolean success = cachedResponse.getStatus() >= 200 && cachedResponse.getStatus() < 300;
        String key = request.getHeader(IdempotencyFilter.HEADER);
        boolean drop = false;

        if (success && request.getRequestURI().endsWith("/messages")) {
            if (body.contains(CONFIRM_RETRY_MARKER)) armDraft(cachedResponse);
            if (body.contains(RETRY_MARKER) && key != null) drop = droppedMessageKeys.add(key);
        } else if (success) {
            Matcher matcher = CONFIRM_PATH.matcher(request.getRequestURI());
            if (matcher.matches()) drop = armedConfirmationDrafts.remove(Long.valueOf(matcher.group(1)));
        }

        if (drop) {
            cachedResponse.reset();
            cachedResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            cachedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(cachedResponse.getOutputStream(), Map.of(
                    "code", "LOCAL_E2E_RESPONSE_LOST",
                    "message", "Tente novamente.",
                    "timestamp", Instant.now().toString()));
        }
        cachedResponse.copyBodyToResponse();
    }

    private void armDraft(ContentCachingResponseWrapper response) throws IOException {
        JsonNode json = mapper.readTree(response.getContentAsByteArray());
        if (json.path("draft").path("id").canConvertToLong()) {
            armedConfirmationDrafts.add(json.path("draft").path("id").longValue());
        }
    }
}
