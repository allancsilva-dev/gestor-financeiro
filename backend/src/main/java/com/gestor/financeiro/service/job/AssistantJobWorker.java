package com.gestor.financeiro.service.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** Consumidor isolado da lane ASSISTANT; nunca reivindica jobs financeiros. */
@Component
public class AssistantJobWorker implements SmartLifecycle {
    private final BackgroundJobService jobs;
    private final Map<String, JobHandler> handlers;
    private final boolean enabled;
    private final int concurrency;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;

    public AssistantJobWorker(BackgroundJobService jobs, List<JobHandler> handlers,
            @Value("${assistant.whatsapp.worker.enabled:false}") boolean enabled,
            @Value("${assistant.whatsapp.worker.concurrency:1}") int concurrency) {
        this.jobs = jobs; this.enabled = enabled; this.concurrency = Math.max(1, concurrency);
        this.handlers = handlers.stream().filter(handler -> handler.type().startsWith("ASSISTANT_"))
                .collect(Collectors.toUnmodifiableMap(JobHandler::type, handler -> handler));
    }
    @Override public void start() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        executor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "assistant-job-worker"); thread.setDaemon(true); return thread;
        });
        for (int index = 0; index < concurrency; index++) {
            int workerIndex = index;
            executor.submit(() -> loop("assistant-" + workerIndex));
        }
    }
    private void loop(String workerId) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<BackgroundJob> claimed = jobs.claim(JobLane.ASSISTANT, workerId, 1, Duration.ofSeconds(120));
                if (claimed.isEmpty()) { Thread.sleep(1_000); continue; }
                BackgroundJob job = claimed.get(0); JobHandler handler = handlers.get(job.type());
                if (handler == null) { jobs.fail(job.id(), workerId, "NO_ASSISTANT_HANDLER", Duration.ZERO); continue; }
                try { handler.handle(job); jobs.complete(job.id(), workerId); }
                catch (Exception failure) { jobs.fail(job.id(), workerId, handler.errorCode(), Duration.ofSeconds(60)); }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            catch (RuntimeException infrastructure) {
                try { Thread.sleep(1_000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    @Override public void stop() { if (running.compareAndSet(true, false) && executor != null) executor.shutdownNow(); }
    @Override public boolean isRunning() { return running.get(); }
    @Override public int getPhase() { return Integer.MAX_VALUE - 90; }
}
