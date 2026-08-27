package com.gestor.financeiro.service.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispara o enfileiramento diário das notificações.
 *
 * <p>O cron só enfileira — o trabalho roda no worker. Sobreposição é barrada em processo
 * ({@code AtomicBoolean}, mesmo padrão de {@code ReconciliacaoScheduler}) e, entre instâncias, pela
 * {@code job_key} determinística por titular e dia.</p>
 */
@Component
public class NotificacaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoScheduler.class);

    private final NotificacaoAgendamentoService agendamento;
    private final boolean habilitado;
    private final AtomicBoolean executando = new AtomicBoolean(false);

    public NotificacaoScheduler(NotificacaoAgendamentoService agendamento,
                                @Value("${app.notificacoes.scheduler.enabled:true}") boolean habilitado) {
        this.agendamento = agendamento;
        this.habilitado = habilitado;
    }

    @Scheduled(cron = "${app.notificacoes.scheduler.cron:0 0 7 * * *}",
            zone = "${app.business.timezone:America/Sao_Paulo}")
    public void executar() {
        if (!habilitado) return;
        if (!executando.compareAndSet(false, true)) {
            log.warn("notificacao_agendamento_sobreposicao_ignorada");
            return;
        }
        try {
            agendamento.enfileirarDoDia();
        } catch (RuntimeException falha) {
            log.error("notificacao_agendamento_erro erro={}", falha.getClass().getSimpleName());
        } finally {
            executando.set(false);
        }
    }
}
