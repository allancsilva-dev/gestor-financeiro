package com.gestor.financeiro.service.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AiExtractionPipeline {
    private final StructuredAiProvider primary;
    private final StructuredAiProvider secondary;
    private final boolean enabled;

    public AiExtractionPipeline(@Qualifier("geminiStructuredAiProvider") StructuredAiProvider primary,
                                @Qualifier("openAiStructuredAiProvider") StructuredAiProvider secondary,
                                @Value("${assistant.external.enabled:false}") boolean enabled) {
        this.primary = primary; this.secondary = secondary; this.enabled = enabled;
    }

    public Optional<ProviderExtraction> extract(ProviderExtractionRequest request) {
        if (!enabled) return Optional.empty();
        try {
            return Optional.of(primary.extract(request, TransactionDraftSchema.VERSION));
        } catch (ProviderFailure first) {
            if (!first.allowsFailover()) throw first;
            try {
                return Optional.of(secondary.extract(request, TransactionDraftSchema.VERSION));
            } catch (ProviderFailure second) {
                if (!second.allowsFailover()) throw second;
                return Optional.empty();
            }
        }
    }
}
