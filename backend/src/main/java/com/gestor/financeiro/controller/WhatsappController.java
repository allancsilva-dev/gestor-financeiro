package com.gestor.financeiro.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.config.IdempotencyFilter;
import com.gestor.financeiro.dto.AssistantDtos.WhatsappLinkResponse;
import com.gestor.financeiro.exception.AssistantException;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.assistant.WhatsappCrypto;
import com.gestor.financeiro.service.assistant.WhatsappService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "assistant.whatsapp.enabled", havingValue = "true")
public class WhatsappController {
    private final WhatsappService whatsapp;
    private final WhatsappCrypto crypto;
    private final ObjectMapper mapper;
    private final AuthenticatedUserService authenticated;
    @Value("${assistant.whatsapp.verify-token:}") private String verifyToken;
    @Value("${assistant.whatsapp.app-secret:}") private String appSecret;

    @PostMapping("/api/v1/assistant/whatsapp/link")
    public ResponseEntity<WhatsappLinkResponse> link(HttpServletRequest request) {
        Object key = request.getAttribute(IdempotencyFilter.ATTRIBUTE);
        if (key == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key obrigatório");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                whatsapp.createLink(authenticated.getAuthenticatedUserId(), key.toString()));
    }

    @GetMapping("/api/v1/webhooks/meta/whatsapp")
    public ResponseEntity<String> verify(@RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token, @RequestParam("hub.challenge") String challenge) {
        if (!"subscribe".equals(mode) || verifyToken.isBlank() || !constantEquals(verifyToken, token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
    }

    @PostMapping(value = "/api/v1/webhooks/meta/whatsapp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> webhook(@RequestBody byte[] raw,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) throws IOException {
        if (!crypto.validSignature(raw, signature, appSecret)) throw new AssistantException(
                "WHATSAPP_SIGNATURE_INVALID", "Assinatura do webhook inválida", HttpStatus.UNAUTHORIZED);
        JsonNode payload = mapper.readTree(raw);
        whatsapp.receive(payload);
        return ResponseEntity.ok().build();
    }

    private boolean constantEquals(String expected, String actual) {
        return actual != null && java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
