package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderResilienceExecutorTest {
    private ProviderResilienceExecutor executor;

    @AfterEach
    void shutdown() {
        if (executor != null) executor.shutdown();
    }

    @Test
    void fazSomenteUmaTentativaAdicionalParaFalhaRetryable() {
        executor = create(2, Duration.ofSeconds(2));
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute("GEMINI", "extract", () -> {
            if (calls.incrementAndGet() == 1) throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "429");
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void erroDeConfiguracaoNaoRecebeRetry() {
        executor = create(2, Duration.ofSeconds(2));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("OPENAI", "extract", () -> {
            calls.incrementAndGet();
            throw new ProviderFailure(ProviderFailure.Kind.CONFIGURATION, "401");
        })).isInstanceOfSatisfying(ProviderFailure.class,
                failure -> assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.CONFIGURATION));
        assertThat(calls).hasValue(1);
    }

    @Test
    void timeoutTotalCancelaChamada() {
        executor = create(1, Duration.ofMillis(80));

        assertThatThrownBy(() -> executor.execute("GEMINI", "extract", () -> {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return "late";
        })).isInstanceOfSatisfying(ProviderFailure.class,
                failure -> assertThat(failure.kind()).isEqualTo(ProviderFailure.Kind.RETRYABLE));
    }

    @Test
    void saturacaoFalhaImediatamenteComRetryAfter() throws Exception {
        executor = create(1, Duration.ofSeconds(2));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var caller = Executors.newSingleThreadExecutor();
        try {
            var running = caller.submit(() -> executor.execute("GEMINI", "extract", () -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return "ok";
            }));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute("OPENAI", "extract", () -> "never"))
                    .isInstanceOfSatisfying(AssistantException.class, failure -> {
                        assertThat(failure.code()).isEqualTo("ASSISTANT_BUSY");
                        assertThat(failure.retryAfterSeconds()).isEqualTo(1L);
                    });
            release.countDown();
            assertThat(running.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    private ProviderResilienceExecutor create(int concurrency, Duration timeout) {
        return new ProviderResilienceExecutor(new SimpleMeterRegistry(), concurrency, 1, timeout, timeout);
    }
}
