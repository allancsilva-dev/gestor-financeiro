package com.gestor.financeiro.service.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Entrega de push pelo serviço do Expo.
 *
 * <p>Decisões deliberadas:</p>
 * <ul>
 *   <li><b>Desligado por padrão.</b> Sem configuração explícita o app não fala com serviço externo;
 *       ambiente de teste e desenvolvimento nunca dispara entrega de verdade.</li>
 *   <li><b>Conteúdo sem PII financeira.</b> O aviso vai para a tela de bloqueio, onde qualquer um
 *       vê: o push leva o tipo do evento e a contagem, e os valores ficam dentro do app.</li>
 *   <li><b>Token recusado é desativado.</b> {@code DeviceNotRegistered} significa app desinstalado
 *       ou permissão revogada; insistir só queima cota e vaza que a conta existe.</li>
 *   <li><b>Falha de entrega não derruba o job.</b> Notificação in-app já foi gravada; push é o
 *       canal, não o fato.</li>
 * </ul>
 */
@Component
public class ExpoPushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    private static final int LOTE_MAXIMO = 100;

    private final PushDispositivoService dispositivos;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient;
    private final boolean habilitado;

    public ExpoPushSender(PushDispositivoService dispositivos, ObjectMapper objectMapper,
                          MeterRegistry meterRegistry, RestClient.Builder restClientBuilder,
                          @Value("${app.notificacoes.push.enabled:false}") boolean habilitado,
                          @Value("${app.notificacoes.push.url:https://exp.host/--/api/v2/push/send}") String url,
                          @Value("${app.notificacoes.push.timeout-seconds:10}") int timeoutSegundos) {
        this.dispositivos = dispositivos;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.habilitado = habilitado;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSegundos));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.restClient = restClientBuilder.baseUrl(url).requestFactory(requestFactory).build();
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Envia um aviso para todos os aparelhos ativos do titular. Silencioso quando desligado. */
    public void enviar(Long usuarioId, String titulo, String corpo) {
        if (!habilitado) return;

        List<String> tokens = dispositivos.ativosDoTitular(usuarioId).stream()
                .map(dispositivo -> dispositivo.getPushToken())
                .toList();
        if (tokens.isEmpty()) return;

        for (int inicio = 0; inicio < tokens.size(); inicio += LOTE_MAXIMO) {
            List<String> lote = tokens.subList(inicio, Math.min(tokens.size(), inicio + LOTE_MAXIMO));
            enviarLote(lote, titulo, corpo);
        }
    }

    private void enviarLote(List<String> tokens, String titulo, String corpo) {
        List<Object> mensagens = new ArrayList<>();
        for (String token : tokens) {
            mensagens.add(java.util.Map.of(
                    "to", token,
                    "title", titulo,
                    "body", corpo,
                    "sound", "default",
                    "priority", "normal"));
        }

        try {
            String resposta = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mensagens)
                    .retrieve()
                    .body(String.class);
            tratarResposta(tokens, resposta);
            meterRegistry.counter("app.push.enviados", "result", "ok").increment(tokens.size());
        } catch (RuntimeException falha) {
            // Push é canal, não fato: a notificação in-app já existe e o usuário a vê ao abrir.
            meterRegistry.counter("app.push.enviados", "result", "falha").increment(tokens.size());
            log.warn("push_envio_falhou destinos={} erro={}", tokens.size(), falha.getClass().getSimpleName());
        }
    }

    private void tratarResposta(List<String> tokens, String resposta) {
        if (resposta == null || resposta.isBlank()) return;
        try {
            JsonNode data = objectMapper.readTree(resposta).path("data");
            if (!data.isArray()) return;
            for (int i = 0; i < data.size() && i < tokens.size(); i++) {
                JsonNode item = data.get(i);
                if (!"error".equals(item.path("status").asText())) continue;
                String erro = item.path("details").path("error").asText();
                if ("DeviceNotRegistered".equals(erro)) {
                    dispositivos.desativarTokenNaoRegistrado(tokens.get(i));
                    meterRegistry.counter("app.push.enviados", "result", "token_invalido").increment();
                } else {
                    log.warn("push_recusado erro={}", erro.isBlank() ? "DESCONHECIDO" : erro);
                }
            }
        } catch (Exception falha) {
            log.warn("push_resposta_ilegivel erro={}", falha.getClass().getSimpleName());
        }
    }
}
