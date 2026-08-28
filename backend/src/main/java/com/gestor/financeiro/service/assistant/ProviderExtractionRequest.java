package com.gestor.financeiro.service.assistant;

public record ProviderExtractionRequest(Long usuarioId, Long conversationId, String text, String trustedContext) {
    public ProviderExtractionRequest {
        if (usuarioId == null) throw new IllegalArgumentException("Titular obrigatório");
        if (text == null || text.isBlank() || text.length() > 2_000) throw new IllegalArgumentException("Texto inválido");
        if (trustedContext != null && trustedContext.length() > 8_000) throw new IllegalArgumentException("Contexto excede 8.000 caracteres");
    }
}
