package com.gestor.financeiro.controller;

import com.gestor.financeiro.config.IdempotencyFilter;
import com.gestor.financeiro.dto.AssistantDtos.AudioResponse;
import com.gestor.financeiro.security.AuthenticatedUserService;
import com.gestor.financeiro.service.assistant.AssistantAudioService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "assistant.audio.enabled", havingValue = "true")
public class AssistantAudioController {
    private final AssistantAudioService audioService;
    private final AuthenticatedUserService authenticated;

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioResponse> audio(@RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "conversationId", required = false) Long conversationId,
            HttpServletRequest request) {
        Object key = request.getAttribute(IdempotencyFilter.ATTRIBUTE);
        if (key == null)
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key obrigatório");
        return ResponseEntity.ok(audioService.transcribe(
                authenticated.getAuthenticatedUserId(), conversationId, audio, key.toString()));
    }
}
