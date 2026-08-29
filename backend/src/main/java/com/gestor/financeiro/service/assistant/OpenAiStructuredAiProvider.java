package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Profile("!local-e2e")
public class OpenAiStructuredAiProvider extends AbstractStructuredAiProvider {
    private final String apiKey;
    private final String model;

    public OpenAiStructuredAiProvider(ObjectMapper mapper, RestClient.Builder builder,
            ProviderResilienceExecutor resilience,
            AssistantUsageBudget budget, AssistantInvocationAudit audit,
            @Value("${assistant.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${assistant.openai.api-key:}") String apiKey,
            @Value("${assistant.openai.model:gpt-5.6-luna}") String model,
            @Value("${assistant.provider.timeout-seconds:8}") int timeoutSeconds) {
        super(mapper, builder.baseUrl(baseUrl).requestFactory(factory(timeoutSeconds)).build(), resilience, budget, audit);
        this.apiKey = apiKey; this.model = model;
    }

    @Override public String provider() { return "OPENAI"; }
    @Override public String model() { return model; }

    @Override
    protected Map<String, Object> payload(ProviderExtractionRequest request) {
        return Map.of("model", model, "store", false,
                "input", List.of(
                        Map.of("role", "developer", "content", "Extraia somente o JSON Schema. Sem ferramentas, SQL, IDs ou confidence."),
                        Map.of("role", "user", "content", "TRUSTED_CONTEXT:\n" + safe(request.trustedContext())
                                + "\nUNTRUSTED_USER_TEXT:\n" + request.text())),
                "text", Map.of("format", Map.of("type", "json_schema", "name", "transaction_draft_v1",
                        "strict", true, "schema", TransactionDraftSchema.jsonSchema())));
    }

    @Override
    protected JsonNode post(Map<String, Object> payload) {
        requireKey();
        return client.post().uri("/v1/responses").header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(JsonNode.class);
    }

    @Override
    protected String outputText(JsonNode response) {
        if (response == null) throw new IllegalArgumentException("resposta vazia");
        for (JsonNode output : response.path("output")) for (JsonNode content : output.path("content")) {
            if ("refusal".equals(content.path("type").asText()))
                throw new ProviderFailure(ProviderFailure.Kind.SAFETY_REFUSAL, "OpenAI recusou a solicitação");
            if ("output_text".equals(content.path("type").asText())) return content.path("text").asText();
        }
        throw new IllegalArgumentException("output_text ausente");
    }

    private void requireKey() { if (apiKey.isBlank()) throw new ProviderFailure(ProviderFailure.Kind.CONFIGURATION, "Chave OpenAI ausente"); }
    private static JdkClientHttpRequestFactory factory(int seconds) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(seconds)).build());
        f.setReadTimeout(Duration.ofSeconds(seconds)); return f;
    }
    private String safe(String value) { return value == null ? "" : value; }
}
