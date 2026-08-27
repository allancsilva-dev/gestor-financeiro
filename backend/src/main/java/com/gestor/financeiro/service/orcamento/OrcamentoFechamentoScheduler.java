package com.gestor.financeiro.service.orcamento;

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
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enfileira o fechamento do mês anterior, um job por titular.
 *
 * <p>Roda todo dia e não só no dia 1: instância parada na virada não pode deixar a competência sem
 * fechar para sempre. A {@code job_key} determinística por titular e competência garante que rodar
 * todo dia — ou em duas instâncias — não fecha duas vezes.</p>
 */
@Component
public class OrcamentoFechamentoScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrcamentoFechamentoScheduler.class);

    private final UsuarioRepository usuarios;
    private final BackgroundJobService jobs;
    private final Clock clock;
    private final boolean habilitado;
    private final int tamanhoDaPagina;
    private final AtomicBoolean executando = new AtomicBoolean(false);

    public OrcamentoFechamentoScheduler(UsuarioRepository usuarios, BackgroundJobService jobs, Clock clock,
                                        @Value("${app.orcamento.fechamento.enabled:true}") boolean habilitado,
                                        @Value("${app.orcamento.fechamento.batch-size:200}") int tamanhoDaPagina) {
        this.usuarios = usuarios;
        this.jobs = jobs;
        this.clock = clock;
        this.habilitado = habilitado;
        this.tamanhoDaPagina = Math.max(1, tamanhoDaPagina);
    }

    @Scheduled(cron = "${app.orcamento.fechamento.cron:0 20 1 * * *}",
            zone = "${app.business.timezone:America/Sao_Paulo}")
    public void executar() {
        if (!habilitado) return;
        if (!executando.compareAndSet(false, true)) {
            log.warn("orcamento_fechamento_sobreposicao_ignorada");
            return;
        }
        try {
            enfileirarCompetenciaAnterior();
        } catch (RuntimeException falha) {
            log.error("orcamento_fechamento_erro erro={}", falha.getClass().getSimpleName());
        } finally {
            executando.set(false);
        }
    }

    /** Enfileira o fechamento da competência anterior e devolve quantos jobs entraram. */
    public int enfileirarCompetenciaAnterior() {
        YearMonth competencia = YearMonth.from(LocalDate.now(clock)).minusMonths(1);
        Instant agora = clock.instant();
        long cursor = 0;
        int enfileirados = 0;

        while (true) {
            List<Long> ids = usuarios.findIdsAfter(cursor, PageRequest.of(0, tamanhoDaPagina));
            if (ids.isEmpty()) break;
            for (Long usuarioId : ids) {
                try {
                    jobs.enqueue(OrcamentoFechamentoJobHandler.TIPO + ":" + usuarioId + ":" + competencia,
                            OrcamentoFechamentoJobHandler.TIPO,
                            "{\"usuarioId\":" + usuarioId + ",\"competencia\":\"" + competencia + "\"}",
                            (short) 1, 0, agora, 3);
                    enfileirados++;
                } catch (RuntimeException falha) {
                    log.warn("orcamento_fechamento_enfileiramento_falhou usuarioId={} erro={}",
                            usuarioId, falha.getClass().getSimpleName());
                }
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < tamanhoDaPagina) break;
        }

        log.info("orcamento_fechamento_enfileirado competencia={} jobs={}", competencia, enfileirados);
        return enfileirados;
    }
}
