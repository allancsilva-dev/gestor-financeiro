package com.gestor.financeiro.service.assistant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRetentionServiceTest {
    @Test
    void apagaFilhosAntesDaConversaEMetricasUsamSomenteRotulosFechados() {
        FakeJdbc jdbc = new FakeJdbc();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();

        new AssistantRetentionService(jdbc, metrics).cleanupExpired();

        assertThat(jdbc.resources).containsExactly("drafts", "messages", "channel_events", "conversations");
        assertThat(metrics.get("app.assistant.retention.deleted").tag("resource", "drafts").counter().count())
                .isEqualTo(1);
        assertThat(metrics.get("app.assistant.retention.deleted").tag("resource", "messages").counter().count())
                .isEqualTo(1);
        assertThat(metrics.get("app.assistant.retention.deleted").tag("resource", "channel_events").counter().count())
                .isEqualTo(1);
        assertThat(metrics.get("app.assistant.retention.deleted").tag("resource", "conversations").counter().count())
                .isEqualTo(1);
    }

    private static final class FakeJdbc extends JdbcTemplate {
        private final List<String> resources = new ArrayList<>();
        @Override public int update(String sql) {
            String normalized = sql.stripLeading();
            if (normalized.startsWith("delete from assistant_drafts")) resources.add("drafts");
            else if (normalized.startsWith("delete from assistant_messages")) resources.add("messages");
            else if (normalized.startsWith("delete from assistant_channel_events")) resources.add("channel_events");
            else if (normalized.startsWith("delete from assistant_conversations")) resources.add("conversations");
            else throw new AssertionError("SQL inesperado: " + sql);
            return 1;
        }
    }
}
