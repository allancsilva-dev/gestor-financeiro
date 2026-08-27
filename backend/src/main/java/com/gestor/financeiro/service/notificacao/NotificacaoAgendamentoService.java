package com.gestor.financeiro.service.notificacao;

import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.service.job.BackgroundJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Enfileira a sincronização diária de notificações.
 *
 * <p>Até aqui, notificação só nascia quando o usuário abria a home
 * ({@code HomeService}) — quem não abria o app não era avisado de fatura vencendo nem de
 * recorrência sem saldo, que é exatamente quando o aviso importa.</p>
 *
 * <p>O agendador só <b>enfileira</b>; quem executa é o worker. Isso mantém o
 * {@code TaskScheduler} (pool 1) livre e, com a {@code job_key} determinística por titular e dia,
 * duas execuções do cron — ou duas instâncias — não geram trabalho duplicado: o
 * {@code UNIQUE (job_key)} da V45 resolve no banco.</p>
 */
@Service
public class NotificacaoAgendamentoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoAgendamentoService.class);

    private final UsuarioRepository usuarios;
    private final BackgroundJobService jobs;
    private final Clock clock;
    private final int tamanhoDaPagina;

    public NotificacaoAgendamentoService(UsuarioRepository usuarios, BackgroundJobService jobs, Clock clock,
                                         @Value("${app.notificacoes.batch-size:200}") int tamanhoDaPagina) {
        this.usuarios = usuarios;
        this.jobs = jobs;
        this.clock = clock;
        this.tamanhoDaPagina = Math.max(1, tamanhoDaPagina);
    }

    /** Enfileira um job por titular e devolve quantos foram enfileirados. */
    public int enfileirarDoDia() {
        LocalDate dia = LocalDate.now(clock);
        Instant agora = clock.instant();
        long cursor = 0;
        int enfileirados = 0;
        int erros = 0;

        while (true) {
            List<Long> ids = usuarios.findIdsAfter(cursor, PageRequest.of(0, tamanhoDaPagina));
            if (ids.isEmpty()) break;
            for (Long usuarioId : ids) {
                try {
                    jobs.enqueue(NotificacaoSyncJobHandler.TIPO + ":" + usuarioId + ":" + dia,
                            NotificacaoSyncJobHandler.TIPO,
                            "{\"usuarioId\":" + usuarioId + ",\"dia\":\"" + dia + "\"}",
                            (short) 1, 0, agora, 3);
                    enfileirados++;
                } catch (RuntimeException falha) {
                    // Falha de um titular não pode interromper a fila do resto — mesmo princípio da
                    // reconciliação global, que isola erro por usuário.
                    erros++;
                    log.warn("notificacao_enfileiramento_falhou usuarioId={} erro={}",
                            usuarioId, falha.getClass().getSimpleName());
                }
            }
            cursor = ids.get(ids.size() - 1);
            if (ids.size() < tamanhoDaPagina) break;
        }

        log.info("notificacao_enfileiramento dia={} enfileirados={} erros={}", dia, enfileirados, erros);
        return enfileirados;
    }
}
