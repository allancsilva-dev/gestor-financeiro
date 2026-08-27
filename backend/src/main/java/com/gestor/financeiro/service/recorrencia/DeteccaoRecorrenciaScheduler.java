package com.gestor.financeiro.service.recorrencia;

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
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enfileira a varredura semanal, um job por titular.
 *
 * <p>Semanal e não diária de propósito: o padrão que a detecção procura leva meses para mudar, e
 * varrer o histórico inteiro todo dia gastaria banco para achar o mesmo resultado. A
 * {@code job_key} carrega a semana, então repetir o cron não duplica trabalho.</p>
 */
@Component
public class DeteccaoRecorrenciaScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeteccaoRecorrenciaScheduler.class);

    private final UsuarioRepository usuarios;
    private final BackgroundJobService jobs;
    private final Clock clock;
    private final boolean habilitado;
    private final int tamanhoDaPagina;
    private final AtomicBoolean executando = new AtomicBoolean(false);

    public DeteccaoRecorrenciaScheduler(UsuarioRepository usuarios, BackgroundJobService jobs, Clock clock,
                                        @Value("${app.recorrencia.deteccao.enabled:true}") boolean habilitado,
                                        @Value("${app.recorrencia.deteccao.batch-size:200}") int tamanhoDaPagina) {
        this.usuarios = usuarios;
        this.jobs = jobs;
        this.clock = clock;
        this.habilitado = habilitado;
        this.tamanhoDaPagina = Math.max(1, tamanhoDaPagina);
    }

    @Scheduled(cron = "${app.recorrencia.deteccao.cron:0 40 4 * * MON}",
            zone = "${app.business.timezone:America/Sao_Paulo}")
    public void executar() {
        if (!habilitado) return;
        if (!executando.compareAndSet(false, true)) {
            log.warn("recorrencia_deteccao_sobreposicao_ignorada");
            return;
        }
        try {
            enfileirarVarredura();
        } catch (RuntimeException falha) {
            log.error("recorrencia_deteccao_erro erro={}", falha.getClass().getSimpleName());
        } finally {
            executando.set(false);
        }
    }

    public int enfileirarVarredura() {
        LocalDate hoje = LocalDate.now(clock);
        String semana = hoje.getYear() + "-W" + hoje.get(WeekFields.of(Locale.ROOT).weekOfWeekBasedYear());
        Instant agora = clock.instant();
        long cursor = 0;
        int enfileirados = 0;

        while (true) {
            List<Long> ids = usuarios.findIdsAfter(cursor, PageRequest.of(0, tamanhoDaPagina));
            if (ids.isEmpty()) break;
            for (Long usuarioId : ids) {
                try {
                    jobs.enqueue(DeteccaoRecorrenciaJobHandler.TIPO + ":" + usuarioId + ":" + semana,
                            DeteccaoRecorrenciaJobHandler.TIPO,
                            "{\"usuarioId\":" + usuarioId + "}", (short) 1, -10, agora, 3);
                    enfileirados++;
                } catch (RuntimeException falha) {
                    log.warn("recorrencia_deteccao_enfileiramento_falhou usuarioId={} erro={}",
                            usuarioId, falha.getClass().getSimpleName());
                }
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < tamanhoDaPagina) break;
        }

        log.info("recorrencia_deteccao_enfileirada semana={} jobs={}", semana, enfileirados);
        return enfileirados;
    }
}
