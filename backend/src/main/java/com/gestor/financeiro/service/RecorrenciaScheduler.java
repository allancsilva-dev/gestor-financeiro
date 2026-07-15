package com.gestor.financeiro.service;

import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.repository.ContaFixaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecorrenciaScheduler {
    private final Clock clock;
    private final ContaFixaRepository contaFixaRepository;
    private final ContaFixaService contaFixaService;

    @Scheduled(cron = "0 5 0 * * *", zone = "${app.business.timezone:America/Sao_Paulo}")
    public void processarAgendadas() {
        processarPendentes();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarAoIniciar() {
        processarPendentes();
    }

    public void processarPendentes() {
        // Repete para recuperar mais de um mês perdido da mesma recorrência.
        for (int rodada = 0; rodada < 120; rodada++) {
            List<Long> ids = contaFixaRepository.findIdsAutomaticasVencidas(LocalDate.now(clock));
            if (ids.isEmpty()) return;
            boolean avancou = false;
            for (Long id : ids) {
                ContaFixa conta = contaFixaRepository.findById(id).orElse(null);
                if (conta == null) continue;
                LocalDate antes = conta.getDataProximoVencimento();
                try {
                    contaFixaService.realizarAutomatica(id);
                } catch (RuntimeException ignored) {
                    // Uma recorrência inválida não impede o processamento das demais.
                }
                ContaFixa depois = contaFixaRepository.findById(id).orElse(null);
                avancou |= depois != null && !antes.equals(depois.getDataProximoVencimento());
            }
            if (!avancou) return; // falhas de saldo continuam pendentes, sem loop quente
        }
    }
}
