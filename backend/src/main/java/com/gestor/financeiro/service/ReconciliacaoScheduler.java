package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.reconciliation.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliacaoScheduler {
    private final ReconciliacaoSistemaService service;
    private final AtomicBoolean executando = new AtomicBoolean();

    @Scheduled(cron = "${app.reconciliation.scheduler.cron:0 30 0 * * *}",
            zone = "${app.business.timezone:America/Sao_Paulo}")
    public void executar() {
        if (!executando.compareAndSet(false, true)) {
            log.warn("reconciliacao_global_sobreposicao_ignorada");
            return;
        }
        try {
            service.executar();
        } finally {
            executando.set(false);
        }
    }
}
