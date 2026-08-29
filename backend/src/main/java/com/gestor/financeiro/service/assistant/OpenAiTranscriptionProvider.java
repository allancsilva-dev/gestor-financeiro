package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Profile("!local-e2e")
public class OpenAiTranscriptionProvider implements TranscriptionProvider {
    private final RestClient client;
    private final ProviderResilienceExecutor resilience;
    private final String apiKey;
    private final String model;

    public OpenAiTranscriptionProvider(RestClient.Builder builder, ProviderResilienceExecutor resilience,
            @Value("${assistant.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${assistant.openai.api-key:}") String apiKey,
            @Value("${assistant.openai.transcription-model:gpt-transcribe}") String model,
            @Value("${assistant.audio.timeout-seconds:45}") int timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(timeout)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.resilience = resilience; this.apiKey = apiKey; this.model = model;
    }
    @Override public String transcribe(Path audio) {
        if (apiKey.isBlank()) throw new ProviderFailure(ProviderFailure.Kind.CONFIGURATION, "Chave OpenAI ausente");
        return resilience.executeTranscription(provider(), () -> {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", model); body.add("file", new FileSystemResource(audio));
            try {
                JsonNode response = client.post().uri("/v1/audio/transcriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(JsonNode.class);
                String text = response == null ? null : response.path("text").asText(null);
                if (text == null || text.isBlank()) throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Transcript ausente");
                return text;
            } catch (RestClientResponseException http) { throw classify(http); }
            catch (ResourceAccessException transport) {
                throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Falha de transporte OpenAI", 0, transport);
            }
        });
    }
    private ProviderFailure classify(RestClientResponseException http) {
        return new ProviderFailure(http.getStatusCode().value() == 429 || http.getStatusCode().is5xxServerError()
                ? ProviderFailure.Kind.RETRYABLE : ProviderFailure.Kind.CONFIGURATION, "OpenAI transcription HTTP " + http.getStatusCode().value());
    }
    @Override public String provider() { return "OPENAI"; }
    @Override public String model() { return model; }
}
