package com.gestor.financeiro.service.assistant;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantRetentionService {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    public AssistantRetentionService(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${assistant.retention.cleanup-cron:0 35 * * * *}")
    @Transactional
    public void cleanupExpired() {
        record("drafts", jdbc.update("delete from assistant_drafts where expires_at <= current_timestamp"));
        record("messages", jdbc.update("delete from assistant_messages where expires_at <= current_timestamp"));
        record("channel_events", jdbc.update(
                "delete from assistant_channel_events where expires_at <= current_timestamp"));
        record("conversations", jdbc.update("""
                delete from assistant_conversations c
                 where c.updated_at < current_timestamp - interval '30 days'
                   and not exists (select 1 from assistant_messages m where m.conversation_id = c.id)
                   and not exists (select 1 from assistant_drafts d where d.conversation_id = c.id)
                   and not exists (select 1 from assistant_whatsapp_links w where w.conversation_id = c.id)
                """));
    }

    private void record(String resource, int count) {
        if (count > 0) metrics.counter("app.assistant.retention.deleted", "resource", resource).increment(count);
    }
}
