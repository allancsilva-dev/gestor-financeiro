package com.gestor.financeiro.service.job;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumidor da fila durável {@code background_jobs}.
 *
 * <p>Decisões que este desenho carrega, todas por causa do envelope real de produção:</p>
 * <ul>
 *   <li><b>Executor próprio.</b> O {@code TaskScheduler} do Spring tem pool 1 e já hospeda
 *       recorrências, reconciliação e limpeza de rate limit; um job longo ali travaria todos.</li>
 *   <li><b>Concorrência baixa por padrão (2).</b> O pool de conexões é 10 e as threads HTTP são 50;
 *       worker guloso faz request falhar por esgotamento de conexão em 5s.</li>
 *   <li><b>Claim fora da transação de trabalho.</b> {@code claim} é transacional; processar dentro
 *       dela esconderia o lease de outros workers até o commit.</li>
 *   <li><b>Renovação de lease em segundo plano.</b> Job longo não pode perder o lease e ser
 *       reivindicado em paralelo.</li>
 * </ul>
 */
@Component
public class BackgroundJobWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);
    private static final String ERRO_SEM_HANDLER = "NO_HANDLER";
    private static final String ERRO_INESPERADO = "UNEXPECTED";

    private final BackgroundJobService jobs;
    private final MeterRegistry meterRegistry;
    private final Map<String, JobHandler> handlers;
    private final boolean habilitado;
    private final int trabalhadores;
    private final Duration intervaloOcioso;
    private final Duration lease;
    private final Duration backoff;
    private final String instancia;

    private final AtomicBoolean rodando = new AtomicBoolean(false);
    private ExecutorService pool;
    private ScheduledExecutorService renovacoes;

    public BackgroundJobWorker(BackgroundJobService jobs, MeterRegistry meterRegistry,
                               List<JobHandler> handlers,
                               @Value("${app.jobs.worker.enabled:true}") boolean habilitado,
                               @Value("${app.jobs.worker.concurrency:2}") int trabalhadores,
                               @Value("${app.jobs.worker.idle-poll-seconds:5}") int intervaloOciosoSegundos,
                               @Value("${app.jobs.worker.lease-seconds:120}") int leaseSegundos,
                               @Value("${app.jobs.worker.retry-backoff-seconds:60}") int backoffSegundos) {
        this.jobs = jobs;
        this.meterRegistry = meterRegistry;
        this.handlers = indexar(handlers);
        this.habilitado = habilitado;
        this.trabalhadores = Math.max(1, trabalhadores);
        this.intervaloOcioso = Duration.ofSeconds(Math.max(1, intervaloOciosoSegundos));
        this.lease = Duration.ofSeconds(Math.max(30, leaseSegundos));
        this.backoff = Duration.ofSeconds(Math.max(1, backoffSegundos));
        this.instancia = "gf-" + Long.toHexString(ProcessHandle.current().pid());
    }

    private static Map<String, JobHandler> indexar(List<JobHandler> handlers) {
        Map<String, JobHandler> mapa = new HashMap<>();
        for (JobHandler handler : handlers) {
            JobHandler anterior = mapa.put(handler.type(), handler);
            if (anterior != null) {
                throw new IllegalStateException("Dois handlers para o mesmo tipo de job: " + handler.type());
            }
        }
        return Map.copyOf(mapa);
    }

    @Override
    public void start() {
        if (!habilitado || !rodando.compareAndSet(false, true)) return;
        pool = Executors.newFixedThreadPool(trabalhadores, tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setName("job-worker-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
        renovacoes();
        for (int i = 0; i < trabalhadores; i++) {
            String workerId = instancia + "-" + i;
            pool.submit(() -> laco(workerId));
        }
        log.info("Worker de jobs iniciado: {} thread(s), tipos {}", trabalhadores, handlers.keySet());
    }

    @Override
    public void stop() {
        if (!rodando.compareAndSet(true, false)) return;
        if (pool != null) pool.shutdownNow();
        synchronized (this) {
            if (renovacoes != null) renovacoes.shutdownNow();
        }
        log.info("Worker de jobs encerrado");
    }

    @Override
    public boolean isRunning() {
        return rodando.get();
    }

    /** Sobe depois do resto do contexto e desce antes, para não pegar bean já fechado. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void laco(String workerId) {
        while (rodando.get() && !Thread.currentThread().isInterrupted()) {
            boolean trabalhou = false;
            try {
                trabalhou = executarUmaRodada(workerId);
            } catch (RuntimeException falha) {
                // Erro de infraestrutura (banco fora, por exemplo) não pode matar a thread.
                log.warn("Rodada do worker {} falhou: {}", workerId, falha.getClass().getSimpleName());
            }
            if (!trabalhou) {
                try {
                    Thread.sleep(intervaloOcioso.toMillis());
                } catch (InterruptedException interrompido) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Processa no máximo um job e diz se havia trabalho. Além do laço, serve para drenagem
     * controlada (teste e operação) sem depender do agendamento de fundo.
     */
    public boolean executarUmaRodada(String workerId) {
        List<BackgroundJob> lote = jobs.claim(workerId, 1, lease);
        if (lote.isEmpty()) return false;
        BackgroundJob job = lote.get(0);
        processar(workerId, job);
        return true;
    }

    private void processar(String workerId, BackgroundJob job) {
        JobHandler handler = handlers.get(job.type());
        if (handler == null) {
            // Tipo desconhecido nunca vai ter sucesso: falha rápido e vai para dead letter.
            jobs.fail(job.id(), workerId, ERRO_SEM_HANDLER, Duration.ZERO);
            contar(job.type(), "sem_handler");
            log.error("Job {} tem tipo sem handler: {}", job.id(), job.type());
            return;
        }

        ScheduledFuture<?> renovacao = agendarRenovacao(workerId, job.id());
        try {
            handler.handle(job);
            if (!jobs.complete(job.id(), workerId)) {
                // Lease perdido no meio: outro worker pode ter reexecutado. Handler idempotente
                // é o que segura essa corrida — por isso o contrato exige.
                log.warn("Job {} concluiu sem lease válido", job.id());
                contar(job.type(), "lease_perdido");
                return;
            }
            contar(job.type(), "concluido");
        } catch (Exception falha) {
            String codigo = codigoSeguro(handler);
            jobs.fail(job.id(), workerId, codigo, backoff);
            contar(job.type(), "falha");
            log.warn("Job {} ({}) falhou: {}", job.id(), job.type(), falha.getClass().getSimpleName());
        } finally {
            renovacao.cancel(false);
        }
    }

    /**
     * Agendador de renovação criado sob demanda: uma rodada avulsa (drenagem manual) também
     * precisa renovar lease, sem depender de o laço de fundo ter sido iniciado.
     */
    private synchronized ScheduledExecutorService renovacoes() {
        if (renovacoes == null || renovacoes.isShutdown()) {
            renovacoes = Executors.newSingleThreadScheduledExecutor(tarefa -> {
                Thread thread = new Thread(tarefa, "job-lease");
                thread.setDaemon(true);
                return thread;
            });
        }
        return renovacoes;
    }

    private ScheduledFuture<?> agendarRenovacao(String workerId, long jobId) {
        long periodo = Math.max(10, lease.toSeconds() / 3);
        return renovacoes().scheduleAtFixedRate(() -> {
            try {
                jobs.renewLease(jobId, workerId, lease);
            } catch (RuntimeException ignorado) {
                // Falha de renovação não interrompe o trabalho: o lease vence e outro worker retoma.
            }
        }, periodo, periodo, TimeUnit.SECONDS);
    }

    private String codigoSeguro(JobHandler handler) {
        String codigo = handler.errorCode();
        return codigo == null || !codigo.matches("[A-Z0-9._:-]{1,120}") ? ERRO_INESPERADO : codigo;
    }

    private void contar(String tipo, String resultado) {
        // Cardinalidade fechada: tipo de job é whitelist de handler, resultado é constante.
        meterRegistry.counter("app.jobs.processed", "type", tipo, "result", resultado).increment();
    }
}
