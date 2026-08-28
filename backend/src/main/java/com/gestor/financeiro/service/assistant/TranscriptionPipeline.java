package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class TranscriptionPipeline {
    private final List<TranscriptionProvider> providers;
    private final AssistantUsageBudget budget;

    public TranscriptionPipeline(List<TranscriptionProvider> providers, AssistantUsageBudget budget) {
        this.providers = providers; this.budget = budget;
    }

    public String transcribe(Long usuarioId, Path audio) {
        RuntimeException last = null;
        for (TranscriptionProvider provider : providers) {
            try {
                budget.reserve(usuarioId);
                String transcript = provider.transcribe(audio);
                if (transcript == null || transcript.isBlank()) throw new IllegalArgumentException("transcript vazio");
                return transcript.trim();
            } catch (ProviderFailure failure) {
                last = failure;
                if (!failure.allowsFailover()) throw failure;
            } catch (RuntimeException failure) { last = failure; }
        }
        throw new AssistantException("PROVIDER_UNAVAILABLE", "Não foi possível transcrever o áudio",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
