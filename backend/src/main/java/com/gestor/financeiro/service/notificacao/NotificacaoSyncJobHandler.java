package com.gestor.financeiro.service.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.model.Notificacao;
import com.gestor.financeiro.model.enums.TipoNotificacao;
import com.gestor.financeiro.service.NotificacaoService;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Deriva as notificações do titular fora do request.
 *
 * <p>Idempotente por construção: {@link NotificacaoService#sincronizar(Long)} usa a chave natural
 * do evento, então reexecutar o job (lease vencido, retentativa) não duplica aviso.</p>
 */
@Component
public class NotificacaoSyncJobHandler implements JobHandler {

    public static final String TIPO = "NOTIFICATION_SYNC";

    /**
     * Título do push por tipo de evento. Sem valor, sem nome de cartão, sem categoria: o aviso
     * aparece na tela de bloqueio, onde qualquer pessoa vê. O detalhe fica dentro do app.
     */
    private static final Map<TipoNotificacao, String> TITULO = Map.ofEntries(
            Map.entry(TipoNotificacao.FATURA_VENCENDO, "Fatura vencendo"),
            Map.entry(TipoNotificacao.PARCELA_AGENDADA, "Parcela chegando"),
            Map.entry(TipoNotificacao.FALHA_SALDO, "Conta fixa não foi paga"),
            Map.entry(TipoNotificacao.ORCAMENTO_ESTOURADO, "Orçamento estourado"),
            Map.entry(TipoNotificacao.META_ATINGIDA, "Meta atingida"),
            // Sem nome de cartao: o titulo aparece na tela de bloqueio.
            Map.entry(TipoNotificacao.LIMITE_ESTOURADO, "Limite do cartão estourado"));

    private static final String CORPO = "Abra o app para ver os detalhes.";

    private final NotificacaoService notificacoes;
    private final ExpoPushSender push;
    private final ObjectMapper objectMapper;

    public NotificacaoSyncJobHandler(NotificacaoService notificacoes, ExpoPushSender push,
                                     ObjectMapper objectMapper) {
        this.notificacoes = notificacoes;
        this.push = push;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return TIPO;
    }

    @Override
    public void handle(BackgroundJob job) throws Exception {
        JsonNode payload = objectMapper.readTree(job.payload());
        long usuarioId = payload.path("usuarioId").asLong();
        if (usuarioId <= 0) {
            throw new IllegalArgumentException("Payload de sincronização sem titular");
        }
        List<Notificacao> novas = notificacoes.sincronizarRetornandoNovas(usuarioId);
        if (novas.isEmpty()) return;
        push.enviar(usuarioId, titulo(novas), CORPO);
    }

    /** Um aviso mostra o tipo; vários viram contagem, para não empilhar push na tela. */
    private String titulo(List<Notificacao> novas) {
        if (novas.size() == 1) {
            return TITULO.getOrDefault(novas.get(0).getTipo(), "Novo aviso");
        }
        return "Você tem " + novas.size() + " avisos novos";
    }
}
