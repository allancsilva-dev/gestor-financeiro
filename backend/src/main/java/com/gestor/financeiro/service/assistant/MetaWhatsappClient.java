package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;

@Component
public class MetaWhatsappClient {
    private final RestClient client;
    private final String accessToken;
    private final String phoneNumberId;
    private final String graphVersion;
    private final HttpClient http;
    private final Duration timeout;
    private final ProviderResilienceExecutor resilience;

    public MetaWhatsappClient(RestClient.Builder builder, ProviderResilienceExecutor resilience,
            @Value("${assistant.meta.base-url:https://graph.facebook.com}") String baseUrl,
            @Value("${assistant.whatsapp.access-token:}") String accessToken,
            @Value("${assistant.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${assistant.whatsapp.graph-version:}") String graphVersion,
            @Value("${assistant.audio.timeout-seconds:45}") int timeoutSeconds) {
        this.client = builder.baseUrl(baseUrl).build(); this.resilience = resilience; this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId; this.graphVersion = graphVersion;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public void sendText(String to, String text) {
        requireConfiguration();
        resilience.execute("META", "send_text", () -> { sendTextOnce(to, text); return null; });
    }

    private void sendTextOnce(String to, String text) {
        try {
            client.post().uri("/{version}/{phone}/messages", graphVersion, phoneNumberId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("messaging_product", "whatsapp", "recipient_type", "individual", "to", to,
                            "type", "text", "text", Map.of("preview_url", false, "body", text)))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException response) { throw classify(response.getStatusCode().value()); }
        catch (ResourceAccessException transport) {
            throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Falha de transporte Meta", 0, transport);
        }
    }

    public DownloadedMedia downloadAudio(String mediaId, long maxBytes) {
        requireConfiguration();
        return resilience.execute("META", "download_audio", () -> downloadAudioOnce(mediaId, maxBytes));
    }

    private DownloadedMedia downloadAudioOnce(String mediaId, long maxBytes) {
        Path temporary = null;
        try {
            JsonNode metadata = client.get().uri("/{version}/{mediaId}", graphVersion, mediaId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class);
            String url = metadata == null ? null : metadata.path("url").asText(null);
            String mime = metadata == null ? null : metadata.path("mime_type").asText(null);
            long declaredSize = metadata == null ? -1 : metadata.path("file_size").asLong(-1);
            if (url == null || !AssistantAudioService.supported(mime) || declaredSize > maxBytes)
                throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Metadados de mídia inválidos");
            URI uri = URI.create(url); requireMetaMediaHost(uri);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) throw classify(response.statusCode());
            temporary = temp();
            try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024]; long total = 0; int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maxBytes) throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Mídia excede 8 MB");
                    output.write(buffer, 0, read);
                }
            }
            DownloadedMedia result = new DownloadedMedia(temporary, mime); temporary = null; return result;
        } catch (ProviderFailure failure) { throw failure; }
        catch (RestClientResponseException response) { throw classify(response.getStatusCode().value()); }
        catch (Exception failure) { throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Falha ao baixar mídia Meta", 0, failure); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { } }
    }

    private ProviderFailure classify(int status) {
        return new ProviderFailure(status == 429 || status >= 500 ? ProviderFailure.Kind.RETRYABLE
                : ProviderFailure.Kind.CONFIGURATION, "Meta HTTP " + status);
    }
    private void requireConfiguration() {
        if (accessToken.isBlank() || phoneNumberId.isBlank() || graphVersion.isBlank())
            throw new ProviderFailure(ProviderFailure.Kind.CONFIGURATION, "Meta Cloud API não configurada");
    }
    private void requireMetaMediaHost(URI uri) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !(host.endsWith(".facebook.com")
                || host.endsWith(".fbcdn.net") || host.endsWith(".fbsbx.com")))
            throw new ProviderFailure(ProviderFailure.Kind.SCHEMA, "Host de mídia Meta inválido");
    }
    private Path temp() throws Exception {
        try { return Files.createTempFile("assistant-whatsapp-audio-", ".media",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
        catch (UnsupportedOperationException ignored) { return Files.createTempFile("assistant-whatsapp-audio-", ".media"); }
    }
    public record DownloadedMedia(Path path, String contentType) { }
}
