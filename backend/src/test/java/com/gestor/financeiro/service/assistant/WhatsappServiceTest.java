package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.service.job.BackgroundJobService;
import com.gestor.financeiro.service.job.JobLane;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void numeroDesconhecidoNaoPersisteNemResponde() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(false);
        FakeJobs jobs = new FakeJobs(jdbc, mapper);
        service(jdbc, jobs).receive(payload("wamid.unknown", "5511999999999", "mercado 50"));
        assertThat(jdbc.externalIds).isEmpty();
        assertThat(jobs.payloads).isEmpty();
    }

    @Test
    void loteCompletoEnfileiraSomenteEventIdEDuplicataNaoReprocessa() throws Exception {
        FakeJdbc jdbc = new FakeJdbc(true);
        FakeJobs jobs = new FakeJobs(jdbc, mapper);
        var service = service(jdbc, jobs);
        var batch = mapper.createObjectNode();
        var entries = batch.putArray("entry");
        entries.addObject().putArray("changes").addObject().putObject("value").putArray("messages")
                .add(message("wamid.1", "5511999999999", "mercado 50"));
        entries.addObject().putArray("changes").addObject().putObject("value").putArray("messages")
                .add(message("wamid.2", "5511999999999", "gasolina 80"));

        service.receive(batch);
        service.receive(batch);

        assertThat(jdbc.externalIds).containsExactlyInAnyOrder("wamid.1", "wamid.2");
        assertThat(jobs.payloads).hasSize(2).allSatisfy(json -> {
            var node = mapper.readTree(json);
            assertThat(node.size()).isEqualTo(1);
            assertThat(node.has("eventId")).isTrue();
            assertThat(json).doesNotContain("mercado", "gasolina", "5511");
        });
    }

    private WhatsappService service(FakeJdbc jdbc, FakeJobs jobs) {
        String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        return new WhatsappService(jdbc, new WhatsappCrypto(encryptionKey, "hmac-secret", "v1"),
                jobs, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private com.fasterxml.jackson.databind.JsonNode payload(String id, String from, String text) {
        var root = mapper.createObjectNode();
        root.putArray("entry").addObject().putArray("changes").addObject().putObject("value")
                .putArray("messages").add(message(id, from, text));
        return root;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode message(String id, String from, String text) {
        var message = mapper.createObjectNode();
        message.put("id", id).put("from", from).put("type", "text").put("timestamp", NOW.getEpochSecond());
        message.putObject("text").put("body", text);
        return message;
    }

    private static final class FakeJdbc extends JdbcTemplate {
        private final boolean linked;
        private final Set<String> externalIds = new HashSet<>();
        private long nextEventId = 40;
        private FakeJdbc(boolean linked) { this.linked = linked; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (sql.contains("select usuario_id from assistant_whatsapp_links"))
                return linked ? (List<T>) List.of(7L) : List.of();
            if (sql.contains("insert into assistant_channel_events")) {
                String externalId = (String) args[1];
                return externalIds.add(externalId) ? (List<T>) List.of(++nextEventId) : List.of();
            }
            throw new AssertionError("SQL inesperado no teste: " + sql);
        }
    }

    private static final class FakeJobs extends BackgroundJobService {
        private final List<String> payloads = new ArrayList<>();
        private FakeJobs(JdbcTemplate jdbc, ObjectMapper mapper) { super(jdbc, mapper); }
        @Override
        public long enqueue(JobLane lane, String key, String type, String payload, short payloadVersion,
                            int priority, Instant availableAt, int maxAttempts) {
            assertThat(lane).isEqualTo(JobLane.ASSISTANT);
            payloads.add(payload);
            return payloads.size();
        }
    }
}
