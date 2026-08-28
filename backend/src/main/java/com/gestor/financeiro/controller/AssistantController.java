package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.IdempotencyFilter;
import com.gestor.financeiro.dto.AssistantDtos.*;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.assistant.AssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "assistant.text.enabled", havingValue = "true")
public class AssistantController {
    private final AssistantService assistant;
    private final com.gestor.financeiro.service.assistant.AssistantRecommendationService recommendations;
    private final AuthenticatedUserService authenticated;

    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> message(@Valid @RequestBody MessageRequest body, HttpServletRequest request) {
        String key = requireIdempotency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                assistant.receive(authenticated.getAuthenticatedUserId(), body, key));
    }

    @GetMapping("/conversations/{id}/messages")
    public List<StoredMessageResponse> messages(@PathVariable Long id) {
        return assistant.listMessages(authenticated.getAuthenticatedUserId(), id);
    }

    @GetMapping("/recommendations")
    public List<RecommendationResponse> recommendations() {
        return recommendations.generate(authenticated.getAuthenticatedUserId());
    }

    @PostMapping("/recommendations/{id}/feedback")
    public ResponseEntity<Void> recommendationFeedback(@PathVariable Long id,
            @Valid @RequestBody RecommendationFeedbackRequest body, HttpServletRequest request) {
        String key = requireIdempotency(request);
        recommendations.feedback(authenticated.getAuthenticatedUserId(), id, body.value(), key);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/drafts/{id}")
    public DraftResponse patch(@PathVariable Long id, @Valid @RequestBody PatchDraftRequest body,
                               HttpServletRequest request) {
        String key = requireIdempotency(request);
        return assistant.patch(authenticated.getAuthenticatedUserId(), id, body, key);
    }

    @PostMapping("/drafts/{id}/confirm")
    public ConfirmationResponse confirm(@PathVariable Long id, @Valid @RequestBody ConfirmDraftRequest body,
                                        HttpServletRequest request) {
        String key = requireIdempotency(request);
        return assistant.confirm(authenticated.getAuthenticatedUserId(), id, body, key);
    }

    @PostMapping("/drafts/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        String key = requireIdempotency(request);
        assistant.cancel(authenticated.getAuthenticatedUserId(), id, key);
        return ResponseEntity.noContent().build();
    }

    private String requireIdempotency(HttpServletRequest request) {
        Object key = request.getAttribute(IdempotencyFilter.ATTRIBUTE);
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key obrigatório");
        }
        return key.toString();
    }
}
