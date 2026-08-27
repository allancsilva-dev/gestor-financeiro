package com.gestor.financeiro.service.meta;

import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.job.BackgroundJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enfileira os aportes do dia, um job por titular.
 *
 * <p>Roda diariamente porque cada meta tem o seu dia; a idempotência por competência é que impede
 * o aporte de acontecer mais de uma vez no mês. A {@code job_key} carrega titular e dia, então o
 * cron repetido não gera trabalho duplicado.</p>
 */
@Component
public class AporteAutomaticoScheduler {

    private static final Logger log = LoggerFactory.getLogger(AporteAutomaticoScheduler.class);

    private final UsuarioRepository usuarios;
    private final BackgroundJobService jobs;
    private final Clock clock;
    private final boolean habilitado;
    private final int tamanhoDaPagina;
    private final AtomicBoolean executando = new AtomicBoolean(false);

    public AporteAutomaticoScheduler(UsuarioRepository usuarios, BackgroundJobService jobs, Clock clock,
                                     @Value("${app.metas.aporte.enabled:true}") boolean habilitado,
                                     @Value("${app.metas.aporte.batch-size:200}") int tamanhoDaPagina) {
        this.usuarios = usuarios;
        this.jobs = jobs;
        this.clock = clock;
        this.habilitado = habilitado;
        this.tamanhoDaPagina = Math.max(1, tamanhoDaPagina);
    }

    @Scheduled(cron = "${app.metas.aporte.cron:0 10 6 * * *}",
            zone = "${app.business.timezone:America/Sao_Paulo}")
    public void executar() {
        if (!habilitado) return;
        if (!executando.compareAndSet(false, true)) {
            log.warn("meta_aporte_sobreposicao_ignorada");
            return;
        }
        try {
            enfileirarDoDia();
        } catch (RuntimeException falha) {
            log.error("meta_aporte_erro erro={}", falha.getClass().getSimpleName());
        } finally {
            executando.set(false);
        }
    }

    public int enfileirarDoDia() {
        LocalDate dia = LocalDate.now(clock);
        Instant agora = clock.instant();
        long cursor = 0;
        int enfileirados = 0;

        while (true) {
            List<Long> ids = usuarios.findIdsAfter(cursor, PageRequest.of(0, tamanhoDaPagina));
            if (ids.isEmpty()) break;
            for (Long usuarioId : ids) {
                try {
                    jobs.enqueue(AporteAutomaticoJobHandler.TIPO + ":" + usuarioId + ":" + dia,
                            AporteAutomaticoJobHandler.TIPO,
                            "{\"usuarioId\":" + usuarioId + "}", (short) 1, 5, agora, 3);
                    enfileirados++;
                } catch (RuntimeException falha) {
                    log.warn("meta_aporte_enfileiramento_falhou usuarioId={} erro={}",
                            usuarioId, falha.getClass().getSimpleName());
                }
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < tamanhoDaPagina) break;
        }

        log.info("meta_aporte_enfileirado dia={} jobs={}", dia, enfileirados);
        return enfileirados;
    }
}
