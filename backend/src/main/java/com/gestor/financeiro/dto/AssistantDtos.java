package com.gestor.financeiro.dto;

import com.gestor.financeiro.model.enums.TipoTransacao;
import com.gestor.financeiro.service.assistant.ParseOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() { }

    public record MessageRequest(Long conversationId, @NotBlank @Size(max = 2000) String text) { }
    public record DraftResponse(Long id, Long version, TipoTransacao tipo, BigDecimal valor,
                                String descricao, LocalDate data, Long carteiraId, Long categoriaId,
                                List<String> missingFields, LocalDateTime expiresAt) { }
    public record MessageResponse(Long conversationId, ParseOutcome outcome, String reply,
                                  DraftResponse draft) { }
    public record StoredMessageResponse(Long id, String role, String content, LocalDateTime createdAt) { }
    public record PatchDraftRequest(Long version, TipoTransacao tipo, BigDecimal valor,
                                    @Size(max = 500) String descricao, LocalDate data,
                                    Long carteiraId, Long categoriaId) { }
    public record ConfirmDraftRequest(Long version) { }
    public record ConfirmationResponse(Long id, Long draftId, Long operationId, Long transactionId,
                                       LocalDateTime confirmedAt) { }
    public record RecommendationResponse(Long id, String rule, String explanation,
                                         LocalDate periodStart, LocalDate periodEnd, List<String> sources,
                                         String actionType, String actionTarget) { }
    public record RecommendationFeedbackRequest(@NotBlank String value) { }
    public record AudioResponse(String transcript, MessageResponse message) { }
    public record WhatsappLinkResponse(String code, LocalDateTime expiresAt) { }
}
