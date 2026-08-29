package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
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
import java.util.List;
import java.util.Map;

@Component
@Order(0)
@Profile("!local-e2e")
public class GeminiTranscriptionProvider implements TranscriptionProvider {
    private final RestClient client;
    private final ProviderResilienceExecutor resilience;
    private final String apiKey;
    private final String model;
    private final MeterRegistry metrics;

    public GeminiTranscriptionProvider(RestClient.Builder builder, ProviderResilienceExecutor resilience, MeterRegistry metrics,
            @Value("${assistant.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${assistant.gemini.api-key:}") String apiKey,
            @Value("${assistant.gemini.transcription-model:gemini-3.5-transcribe}") String model,
            @Value("${assistant.audio.timeout-seconds:45}") int timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(timeout)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeout));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.resilience = resilience; this.metrics = metrics; this.apiKey = apiKey; this.model = model;
    }
    @Override public String transcribe(Path audio) {
        if (apiKey.isBlank()) throw new ProviderFailure(ProviderFailure.Kind.CONFIGURATION, "Chave Gemini ausente");
        return resilience.executeTranscription(provider(), () -> transcribeOnce(audio));
    }
    private String transcribeOnce(Path audio) {
        String remoteName = null;
        try {
            MultiValueMap<String, Object> upload = new LinkedMultiValueMap<>();
            upload.add("file", new FileSystemResource(audio));
            JsonNode uploaded = client.post().uri("/upload/v1beta/files?key={key}", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(upload).retrieve().body(JsonNode.class);
            remoteName = uploaded == null ? null : uploaded.path("file").path("name").asText(null);
            String uri = uploaded == null ? null : uploaded.path("file").path("uri").asText(null);
            if (remoteName == null || uri == null) throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Arquivo remoto ausente");
            Map<String, Object> payload = Map.of("model", model, "input", List.of(
                    Map.of("role", "user", "content", List.of(
                            Map.of("type", "input_text", "text", "Transcreva literalmente este áudio em português. Retorne somente o transcript."),
                            Map.of("type", "input_file", "file_uri", uri)))));
            JsonNode response = client.post().uri("/v1beta/interactions?key={key}", apiKey)
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(JsonNode.class);
            String text = response == null ? null : response.path("outputs").path(0).path("text").asText(null);
            if (text == null || text.isBlank()) throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Transcript ausente");
            return text;
        } catch (RestClientResponseException http) {
            throw new ProviderFailure(http.getStatusCode().value() == 429 || http.getStatusCode().is5xxServerError()
                    ? ProviderFailure.Kind.RETRYABLE : ProviderFailure.Kind.CONFIGURATION, "Gemini transcription HTTP " + http.getStatusCode().value());
        } catch (ResourceAccessException transport) {
            throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Falha de transporte Gemini", 0, transport);
        } finally {
            if (remoteName != null) try { client.delete().uri("/v1beta/{name}?key={key}", remoteName, apiKey).retrieve().toBodilessEntity(); }
            catch (RuntimeException cleanup) {
                metrics.counter("app.assistant.audio.cleanup.failures", "location", "gemini_remote").increment();
            }
        }
    }
    @Override public String provider() { return "GEMINI"; }
    @Override public String model() { return model; }
}
