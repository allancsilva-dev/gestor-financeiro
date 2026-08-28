package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssistantAudioServiceTest {
    private AssistantAudioService service;

    @AfterEach void tearDown() { if (service != null) service.shutdown(); }

    @Test
    void replayComMesmaChaveNaoChamaTranscricaoNemParserNovamente() {
        AssistantService assistant = mock(AssistantService.class);
        TranscriptionPipeline transcription = mock(TranscriptionPipeline.class);
        AssistantMutationReplay replay = mock(AssistantMutationReplay.class);
        AtomicReference<com.gestor.financeiro.dto.AssistantDtos.AudioResponse> stored = new AtomicReference<>();
        when(replay.find(eq(7L), eq("assistant:audio:replay"), anyString(),
                eq(com.gestor.financeiro.dto.AssistantDtos.AudioResponse.class)))
                .thenAnswer(call -> java.util.Optional.ofNullable(stored.get()));
        doAnswer(call -> { stored.set(call.getArgument(5)); return null; }).when(replay)
                .store(eq(7L), isNull(), eq("TRANSCRIBE_AUDIO"), eq("assistant:audio:replay"), anyString(), any());
        when(transcription.transcribe(eq(7L), any())).thenReturn("mercado 50 ontem");
        var message = new com.gestor.financeiro.dto.AssistantDtos.MessageResponse(
                9L, ParseOutcome.COMPLETE, "Rascunho pronto", null);
        when(assistant.receive(eq(7L), any(), anyString())).thenReturn(message);
        service = new AssistantAudioService(assistant, transcription, new SimpleMeterRegistry(),
                replay, 8_388_608, 60, 45, 1);

        var first = service.transcribe(7L, null, file(30), "assistant:audio:replay");
        var repeated = service.transcribe(7L, null, file(30), "assistant:audio:replay");

        assertThat(repeated).isEqualTo(first);
        verify(transcription, times(1)).transcribe(eq(7L), any());
        verify(assistant, times(1)).receive(eq(7L), any(), anyString());
    }

    @Test
    void transcreveM4aValidoPassaTranscriptAoMesmoPipelineEApagaTemporario() {
        AssistantService assistant = mock(AssistantService.class);
        TranscriptionPipeline transcription = mock(TranscriptionPipeline.class);
        AtomicReference<Path> temporary = new AtomicReference<>();
        when(transcription.transcribe(eq(7L), any())).thenAnswer(call -> {
            temporary.set(call.getArgument(1));
            assertThat(Files.exists(temporary.get())).isTrue();
            return "mercado 50 ontem";
        });
        service = new AssistantAudioService(assistant, transcription, new SimpleMeterRegistry(),
                mock(AssistantMutationReplay.class), 8_388_608, 60, 45, 1);

        var response = service.transcribe(7L, 9L, file(30));

        assertThat(response.transcript()).isEqualTo("mercado 50 ontem");
        verify(assistant).receive(eq(7L), argThat(request -> request.conversationId().equals(9L)
                && request.text().equals("mercado 50 ontem")));
        assertThat(Files.exists(temporary.get())).isFalse();
    }

    @Test
    void recusaMimeForjadoEDuracaoAcimaDoLimite() {
        service = new AssistantAudioService(mock(AssistantService.class), mock(TranscriptionPipeline.class),
                new SimpleMeterRegistry(), mock(AssistantMutationReplay.class), 8_388_608, 60, 45, 1);
        assertThatThrownBy(() -> service.transcribe(7L, null,
                new MockMultipartFile("audio", "voice.m4a", "audio/mp4", "not audio".getBytes())))
                .isInstanceOfSatisfying(AssistantException.class, error -> assertThat(error.code()).isEqualTo("AUDIO_INVALID"));
        assertThatThrownBy(() -> service.transcribe(7L, null, file(61)))
                .isInstanceOfSatisfying(AssistantException.class, error -> assertThat(error.code()).isEqualTo("AUDIO_INVALID"));
        assertThatThrownBy(() -> service.transcribe(7L, null, ogg(61)))
                .isInstanceOfSatisfying(AssistantException.class, error -> assertThat(error.code()).isEqualTo("AUDIO_INVALID"));
    }

    @Test
    void timeoutCancelaTranscricaoEApagaArquivoNoFinally() throws Exception {
        TranscriptionPipeline transcription = mock(TranscriptionPipeline.class);
        AtomicReference<Path> temporary = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(transcription.transcribe(eq(7L), any())).thenAnswer(call -> {
            temporary.set(call.getArgument(1)); entered.countDown();
            try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return "tarde demais";
        });
        service = new AssistantAudioService(mock(AssistantService.class), transcription,
                new SimpleMeterRegistry(), mock(AssistantMutationReplay.class), 8_388_608, 60, 1, 1);

        try {
            assertThatThrownBy(() -> service.transcribe(7L, null, file(30)))
                    .isInstanceOfSatisfying(AssistantException.class, error ->
                            assertThat(error.code()).isEqualTo("PROVIDER_UNAVAILABLE"));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(temporary.get()).isNotNull();
            assertThat(Files.exists(temporary.get())).isFalse();
        } finally {
            release.countDown();
        }
    }

    private MockMultipartFile file(int seconds) {
        byte[] bytes = new byte[64];
        bytes[3] = 64; bytes[4] = 'f'; bytes[5] = 't'; bytes[6] = 'y'; bytes[7] = 'p';
        bytes[20] = 'm'; bytes[21] = 'v'; bytes[22] = 'h'; bytes[23] = 'd'; bytes[24] = 0;
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(36, 1_000).putInt(40, seconds * 1_000);
        return new MockMultipartFile("audio", "voice.m4a", "audio/mp4", bytes);
    }

    private MockMultipartFile ogg(int seconds) {
        byte[] bytes = new byte[36];
        bytes[0] = 'O'; bytes[1] = 'g'; bytes[2] = 'g'; bytes[3] = 'S';
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(6, seconds * 48_000L);
        bytes[26] = 1; bytes[27] = 8;
        byte[] opus = "OpusHead".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(opus, 0, bytes, 28, opus.length);
        return new MockMultipartFile("audio", "voice.ogg", "audio/ogg; codecs=opus", bytes);
    }
}
