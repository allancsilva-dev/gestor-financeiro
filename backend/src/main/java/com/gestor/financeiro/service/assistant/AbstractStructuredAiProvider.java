package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Locale;
import java.util.Map;

abstract class AbstractStructuredAiProvider implements StructuredAiProvider {
    protected final ObjectMapper mapper;
    protected final RestClient client;
    private final ProviderResilienceExecutor resilience;
    private final AssistantUsageBudget budget;
    private final AssistantInvocationAudit audit;

    protected AbstractStructuredAiProvider(ObjectMapper mapper, RestClient client, ProviderResilienceExecutor resilience,
                                           AssistantUsageBudget budget, AssistantInvocationAudit audit) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.client = client; this.resilience = resilience; this.budget = budget; this.audit = audit;
    }

    @Override
    public ProviderExtraction extract(ProviderExtractionRequest request, String schemaVersion) {
        if (!TransactionDraftSchema.VERSION.equals(schemaVersion)) throw new ProviderFailure(
                ProviderFailure.Kind.CONFIGURATION, "Schema não suportado");
        return resilience.execute(provider(), "extract", () -> {
            try {
                budget.reserve(request.usuarioId());
                JsonNode response = post(payload(request));
                String json = outputText(response);
                TransactionDraftV1 draft = mapper.readValue(json, TransactionDraftV1.class);
                validate(draft);
                audit.record(request.usuarioId(), request.conversationId(), provider(), model(), "SUCCESS");
                return new ProviderExtraction(draft, provider(), model());
            } catch (RestClientResponseException http) {
                audit.record(request.usuarioId(), request.conversationId(), provider(), model(), "HTTP_ERROR");
                throw classify(http);
            } catch (ResourceAccessException transport) {
                audit.record(request.usuarioId(), request.conversationId(), provider(), model(), "TRANSPORT_ERROR");
                throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE,
                        "Falha de transporte do fornecedor", 0, transport);
            } catch (JsonProcessingException | IllegalArgumentException invalid) {
                audit.record(request.usuarioId(), request.conversationId(), provider(), model(), "SCHEMA_ERROR");
                throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Resposta fora do schema", 0, invalid);
            }
        });
    }

    protected abstract Map<String, Object> payload(ProviderExtractionRequest request);
    protected abstract JsonNode post(Map<String, Object> payload);
    protected abstract String outputText(JsonNode response);

    private ProviderFailure classify(RestClientResponseException http) {
        HttpStatusCode status = http.getStatusCode();
        String body = http.getResponseBodyAsString().toLowerCase(Locale.ROOT);
        if (status.value() == 429 || status.is5xxServerError()) {
            long retryAfter = parseRetryAfter(http.getResponseHeaders() == null ? null : http.getResponseHeaders().getFirst("Retry-After"));
            return new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Fornecedor temporariamente indisponível", retryAfter, http);
        }
        if (body.contains("safety") || body.contains("refusal") || body.contains("blocked"))
            return new ProviderFailure(ProviderFailure.Kind.SAFETY_REFUSAL, "Fornecedor recusou a solicitação");
        return new ProviderFailure(ProviderFailure.Kind.CONFIGURATION, "Erro de autenticação, configuração ou request", 0, http);
    }

    private long parseRetryAfter(String value) {
        try { return value == null ? 0 : Math.min(2_000, Long.parseLong(value.trim()) * 1_000); }
        catch (NumberFormatException ignored) { return 0; }
    }
    private void validate(TransactionDraftV1 draft) {
        if (draft == null || draft.missingFields() == null) throw new IllegalArgumentException("draft incompleto");
    }
}
