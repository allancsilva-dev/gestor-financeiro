package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.dto.AssistantDtos.AudioResponse;
import com.gestor.financeiro.dto.AssistantDtos.MessageRequest;
import com.gestor.financeiro.exception.AssistantException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import com.gestor.financeiro.service.OperacaoFinanceiraService;
import java.util.concurrent.*;

@Service
public class AssistantAudioService {
    private final AssistantService assistant;
    private final TranscriptionPipeline transcription;
    private final MeterRegistry metrics;
    private final AssistantMutationReplay mutationReplay;
    private final long maxBytes;
    private final int maxSeconds;
    private final int timeoutSeconds;
    private final Semaphore admission;
    private final ExecutorService executor;

    public AssistantAudioService(AssistantService assistant, TranscriptionPipeline transcription, MeterRegistry metrics,
            AssistantMutationReplay mutationReplay,
            @Value("${assistant.limits.audio-bytes:8388608}") long maxBytes,
            @Value("${assistant.limits.audio-seconds:60}") int maxSeconds,
            @Value("${assistant.audio.timeout-seconds:45}") int timeoutSeconds,
            @Value("${assistant.limits.transcription-global-concurrency:1}") int concurrency) {
        this.assistant = assistant; this.transcription = transcription; this.metrics = metrics;
        this.mutationReplay = mutationReplay;
        this.maxBytes = maxBytes; this.maxSeconds = maxSeconds; this.timeoutSeconds = timeoutSeconds;
        this.admission = new Semaphore(Math.max(1, concurrency), true);
        this.executor = Executors.newFixedThreadPool(Math.max(1, concurrency), runnable -> {
            Thread thread = new Thread(runnable, "assistant-audio"); thread.setDaemon(true); return thread;
        });
    }

    @PreDestroy void shutdown() { executor.shutdownNow(); }

    public AudioResponse transcribe(Long usuarioId, Long conversationId, MultipartFile audio) {
        return transcribe(usuarioId, conversationId, audio, null);
    }

    public AudioResponse transcribe(Long usuarioId, Long conversationId, MultipartFile audio, String idempotencyKey) {
        validateBasic(audio);
        Path temporary = null;
        try {
            temporary = createTemporary(".audio");
            audio.transferTo(temporary);
            String requestHash = audioHash(conversationId, temporary);
            if (idempotencyKey != null) {
                mutationReplay.lockUser(usuarioId);
                var replay = mutationReplay.find(usuarioId, idempotencyKey, requestHash, AudioResponse.class);
                if (replay.isPresent()) return replay.get();
            }
            String transcript = transcribeFile(usuarioId, temporary, audio.getContentType());
            temporary = null; // transcribeFile assume a limpeza
            String messageKey = "assistant:audio-message:" + requestHash.substring(0, 32);
            MessageRequest message = new MessageRequest(conversationId, transcript);
            AudioResponse response = new AudioResponse(transcript, idempotencyKey == null
                    ? assistant.receive(usuarioId, message)
                    : assistant.receive(usuarioId, message, messageKey));
            if (idempotencyKey != null) mutationReplay.store(usuarioId, conversationId,
                    "TRANSCRIBE_AUDIO", idempotencyKey, requestHash, response);
            return response;
        } catch (IOException failure) {
            throw invalid("Não foi possível ler o áudio");
        } finally { deleteTemporary(temporary); }
    }

    private String audioHash(Long conversationId, Path temporary) throws IOException {
        java.security.MessageDigest digest;
        try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        try (InputStream input = Files.newInputStream(temporary)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        String fileHash = java.util.HexFormat.of().formatHex(digest.digest());
        return OperacaoFinanceiraService.hashPayload(
                (conversationId == null ? "NEW" : conversationId) + "\n" + fileHash);
    }

    /** Entrada compartilhada pelo app e pelo worker WhatsApp; sempre remove o arquivo recebido. */
    public String transcribeFile(Long usuarioId, Path temporary, String contentType) {
        if (!admission.tryAcquire()) {
            deleteTemporary(temporary);
            throw new AssistantException("ASSISTANT_BUSY", "Transcrição ocupada; tente novamente em instantes",
                    HttpStatus.TOO_MANY_REQUESTS, 2);
        }
        Future<String> task = null;
        try {
            validateAudioFile(temporary, contentType);
            Path input = temporary;
            task = executor.submit(() -> transcription.transcribe(usuarioId, input));
            String transcript = task.get(timeoutSeconds, TimeUnit.SECONDS);
            return transcript;
        } catch (TimeoutException timeout) {
            if (task != null) task.cancel(true);
            throw new AssistantException("PROVIDER_UNAVAILABLE", "A transcrição demorou demais; tente novamente",
                    HttpStatus.GATEWAY_TIMEOUT);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssistantException("PROVIDER_UNAVAILABLE", "Transcrição interrompida", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ExecutionException execution) {
            if (execution.getCause() instanceof RuntimeException runtime) throw runtime;
            throw invalid("Falha ao processar áudio");
        } catch (IOException failure) {
            throw invalid("Não foi possível ler o áudio");
        } finally {
            deleteTemporary(temporary);
            admission.release();
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) return;
        try { Files.deleteIfExists(temporary); }
        catch (IOException cleanup) { metrics.counter("app.assistant.audio.cleanup.failures", "location", "local").increment(); }
    }

    private void validateBasic(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) throw invalid("Áudio vazio");
        if (audio.getSize() > maxBytes) throw invalid("Áudio excede 8 MB");
        String contentType = audio.getContentType();
        if (!supported(contentType)) throw invalid("Formato de áudio inválido; grave novamente pelo app");
    }

    static boolean supported(String contentType) {
        if (contentType == null) return false;
        String mime = contentType.toLowerCase(java.util.Locale.ROOT).split(";", 2)[0].trim();
        return mime.equals("audio/mp4") || mime.equals("audio/m4a") || mime.equals("audio/x-m4a") || mime.equals("audio/ogg");
    }

    private Path createTemporary(String suffix) throws IOException {
        try { return Files.createTempFile("assistant-audio-", suffix, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
        catch (UnsupportedOperationException ignored) { return Files.createTempFile("assistant-audio-", suffix); }
    }

    private void validateAudioFile(Path path, String contentType) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > maxBytes) throw invalid("Áudio excede 8 MB");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) { bytes = input.readNBytes((int) Math.min(size, maxBytes)); }
        String mime = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (mime.startsWith("audio/ogg")) { validateOgg(bytes); return; }
        validateM4a(bytes);
    }

    private void validateM4a(byte[] bytes) {
        if (bytes.length < 12 || bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p')
            throw invalid("Conteúdo não corresponde a um áudio M4A");
        int mvhd = find(bytes, new byte[]{'m','v','h','d'});
        if (mvhd < 0 || mvhd + 24 >= bytes.length) throw invalid("Duração do áudio indisponível");
        int version = bytes[mvhd + 4] & 0xff;
        int scaleOffset = version == 1 ? mvhd + 24 : mvhd + 16;
        int durationOffset = scaleOffset + 4;
        if (durationOffset + (version == 1 ? 8 : 4) > bytes.length) throw invalid("Áudio incompleto");
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        long scale = Integer.toUnsignedLong(buffer.getInt(scaleOffset));
        long duration = version == 1 ? buffer.getLong(durationOffset) : Integer.toUnsignedLong(buffer.getInt(durationOffset));
        if (scale <= 0 || duration < 0 || duration / (double) scale > maxSeconds) throw invalid("Áudio excede 60 segundos");
    }
    private void validateOgg(byte[] bytes) {
        if (bytes.length < 32 || !startsAt(bytes, 0, new byte[]{'O','g','g','S'})
                || find(bytes, "OpusHead".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) < 0)
            throw invalid("Conteúdo não corresponde a áudio Ogg/Opus");
        long lastGranule = -1; int offset = 0;
        while (offset + 27 <= bytes.length && startsAt(bytes, offset, new byte[]{'O','g','g','S'})) {
            int segments = bytes[offset + 26] & 0xff;
            if (offset + 27 + segments > bytes.length) throw invalid("Áudio Ogg incompleto");
            int body = 0; for (int i = 0; i < segments; i++) body += bytes[offset + 27 + i] & 0xff;
            long granule = ByteBuffer.wrap(bytes, offset + 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
            if (granule >= 0) lastGranule = granule;
            offset += 27 + segments + body;
        }
        if (lastGranule < 0 || lastGranule / 48_000d > maxSeconds) throw invalid("Áudio excede 60 segundos");
    }
    private boolean startsAt(byte[] value, int offset, byte[] token) {
        if (offset < 0 || offset + token.length > value.length) return false;
        for (int i = 0; i < token.length; i++) if (value[offset + i] != token[i]) return false;
        return true;
    }
    private int find(byte[] value, byte[] token) {
        outer: for (int i = 0; i <= value.length - token.length; i++) {
            for (int j = 0; j < token.length; j++) if (value[i + j] != token[j]) continue outer;
            return i;
        }
        return -1;
    }
    private AssistantException invalid(String message) { return new AssistantException("AUDIO_INVALID", message, HttpStatus.BAD_REQUEST); }
}
