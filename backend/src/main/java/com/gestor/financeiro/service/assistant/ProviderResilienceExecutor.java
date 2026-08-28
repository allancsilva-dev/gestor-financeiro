package com.gestor.financeiro.service.assistant;

import com.gestor.financeiro.exception.AssistantException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Component
public class ProviderResilienceExecutor {
    private final Bulkhead textBulkhead;
    private final Bulkhead transcriptionBulkhead;
    private final Map<String, CircuitBreaker> circuits = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final MeterRegistry metrics;
    private final Duration textTimeout;
    private final Duration transcriptionTimeout;

    public ProviderResilienceExecutor(MeterRegistry metrics,
            @Value("${assistant.limits.text-global-concurrency:2}") int concurrency,
            @Value("${assistant.limits.transcription-global-concurrency:1}") int transcriptionConcurrency,
            @Value("${assistant.providers.text-timeout:12s}") Duration textTimeout,
            @Value("${assistant.providers.transcription-timeout:45s}") Duration transcriptionTimeout) {
        this.metrics = metrics;
        this.textBulkhead = bulkhead("assistant-text", concurrency);
        this.transcriptionBulkhead = bulkhead("assistant-transcription", transcriptionConcurrency);
        this.executor = Executors.newFixedThreadPool(Math.max(1, concurrency + transcriptionConcurrency), runnable -> {
            Thread thread = new Thread(runnable, "assistant-provider");
            thread.setDaemon(true);
            return thread;
        });
        this.textTimeout = textTimeout;
        this.transcriptionTimeout = transcriptionTimeout;
    }

    public <T> T execute(String provider, String operation, Supplier<T> call) {
        return execute(textBulkhead, textTimeout, provider, operation, call);
    }

    public <T> T executeTranscription(String provider, Supplier<T> call) {
        return execute(transcriptionBulkhead, transcriptionTimeout, provider, "transcription", call);
    }

    private <T> T execute(Bulkhead bulkhead, Duration timeout, String provider, String operation, Supplier<T> call) {
        if (!bulkhead.tryAcquirePermission()) {
            count(provider, operation, "busy");
            throw new AssistantException("ASSISTANT_BUSY", "Assistente ocupado; tente novamente em instantes",
                    HttpStatus.TOO_MANY_REQUESTS, 1);
        }
        try {
            CircuitBreaker circuit = circuits.computeIfAbsent(provider + ':' + operation, this::circuitBreaker);
            Retry retry = retry(provider + ':' + operation);
            Supplier<T> resilient = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuit, call));
            TimeLimiter limiter = TimeLimiter.of(provider + ':' + operation, TimeLimiterConfig.custom()
                    .timeoutDuration(timeout).cancelRunningFuture(true).build());
            T result = limiter.executeFutureSupplier(() -> executor.submit(resilient::get));
            count(provider, operation, "success");
            return result;
        } catch (CallNotPermittedException open) {
            count(provider, operation, "circuit_open");
            throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Circuit breaker aberto", 0, open);
        } catch (TimeoutException timeoutFailure) {
            count(provider, operation, "timeout");
            throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Timeout total do fornecedor", 0, timeoutFailure);
        } catch (ExecutionException wrapped) {
            RuntimeException failure = unwrap(wrapped);
            count(provider, operation, outcome(failure));
            throw failure;
        } catch (RuntimeException failure) {
            count(provider, operation, outcome(failure));
            throw failure;
        } catch (Exception failure) {
            count(provider, operation, "failure");
            throw new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Falha ao executar fornecedor", 0, failure);
        } finally {
            bulkhead.onComplete();
        }
    }

    private Bulkhead bulkhead(String name, int concurrency) {
        return Bulkhead.of(name, BulkheadConfig.custom().maxConcurrentCalls(Math.max(1, concurrency))
                .maxWaitDuration(Duration.ZERO).fairCallHandlingStrategyEnabled(true).build());
    }

    private CircuitBreaker circuitBreaker(String name) {
        return CircuitBreaker.of(name, CircuitBreakerConfig.custom().failureRateThreshold(100)
                .minimumNumberOfCalls(5).slidingWindowSize(5).waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1).recordException(this::recordable).build());
    }

    private Retry retry(String name) {
        RetryConfig config = RetryConfig.custom().maxAttempts(2).retryOnException(this::retryable)
                .intervalBiFunction((attempt, result) -> {
                    Throwable failure = result.isLeft() ? result.getLeft() : null;
                    long requested = failure instanceof ProviderFailure p ? p.retryAfterMillis() : 0;
                    long exponential = Math.min(600, 150L << Math.max(0, attempt - 1));
                    return Math.max(requested, ThreadLocalRandom.current().nextLong(
                            Math.max(1, exponential / 2), exponential + 1));
                }).build();
        return Retry.of(name, config);
    }

    private boolean retryable(Throwable failure) {
        return failure instanceof ProviderFailure p && p.kind() == ProviderFailure.Kind.RETRYABLE;
    }

    private boolean recordable(Throwable failure) {
        return failure instanceof ProviderFailure p && p.kind() == ProviderFailure.Kind.RETRYABLE;
    }

    private RuntimeException unwrap(ExecutionException wrapped) {
        Throwable cause = wrapped.getCause();
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null)
            cause = cause.getCause();
        if (cause instanceof CallNotPermittedException open)
            return new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Circuit breaker aberto", 0, open);
        return cause instanceof RuntimeException runtime ? runtime
                : new ProviderFailure(ProviderFailure.Kind.RETRYABLE, "Falha ao executar fornecedor", 0, cause);
    }

    private String outcome(Throwable failure) {
        return failure instanceof ProviderFailure p ? p.kind().name().toLowerCase() : "failure";
    }

    private void count(String provider, String operation, String result) {
        metrics.counter("app.assistant.provider.calls", "provider", provider,
                "operation", operation, "result", result).increment();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
