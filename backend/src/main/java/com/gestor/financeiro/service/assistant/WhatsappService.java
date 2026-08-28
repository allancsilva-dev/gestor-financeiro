package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.AssistantDtos.WhatsappLinkResponse;
import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.service.job.BackgroundJobService;
import com.gestor.financeiro.service.job.JobLane;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WhatsappService {
    public static final String JOB_TYPE = "ASSISTANT_WHATSAPP_EVENT";
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final JdbcTemplate jdbc;
    private final WhatsappCrypto crypto;
    private final BackgroundJobService jobs;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WhatsappService(JdbcTemplate jdbc, WhatsappCrypto crypto, BackgroundJobService jobs,
                           ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc; this.crypto = crypto; this.jobs = jobs; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public WhatsappLinkResponse createLink(Long usuarioId) {
        return createLink(usuarioId, null);
    }

    @Transactional
    public WhatsappLinkResponse createLink(Long usuarioId, String idempotencyKey) {
        if (idempotencyKey != null) {
            Long locked = jdbc.queryForObject("select id from usuarios where id = ? for update", Long.class, usuarioId);
            if (locked == null) throw new com.gestor.financeiro.exception.ResourceNotFoundException("Usuário não encontrado");
            List<WhatsappLinkResponse> replay = jdbc.query("""
                    select code_ciphertext, expires_at from assistant_whatsapp_links
                     where usuario_id = ? and idempotency_key = ? and used_at is null
                       and expires_at > current_timestamp
                    """, (rs, row) -> new WhatsappLinkResponse(
                    crypto.decrypt(rs.getString("code_ciphertext")), rs.getTimestamp("expires_at").toLocalDateTime()),
                    usuarioId, idempotencyKey);
            if (!replay.isEmpty()) return replay.get(0);
        }
        Integer active = jdbc.queryForObject("select count(*) from assistant_whatsapp_links where usuario_id = ? and used_at is not null",
                Integer.class, usuarioId);
        if (active != null && active > 0) throw new BusinessException("WhatsApp já está conectado");
        String code = code(); LocalDateTime now = LocalDateTime.now(clock); LocalDateTime expires = now.plusMinutes(10);
        jdbc.update("delete from assistant_whatsapp_links where usuario_id = ? and used_at is null", usuarioId);
        jdbc.update("""
                insert into assistant_whatsapp_links(usuario_id, code_hash, code_ciphertext,
                  idempotency_key, expires_at, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, usuarioId, crypto.hmac(code), crypto.encrypt(code), idempotencyKey, expires, now);
        return new WhatsappLinkResponse(code, expires);
    }

    /** Assinatura já validada pelo controller. Número desconhecido não produz resposta nem persistência. */
    @Transactional
    public void receive(JsonNode root) {
        JsonNode entries = root.path("entry");
        if (!entries.isArray()) return;
        for (JsonNode entry : entries) {
            JsonNode changes = entry.path("changes");
            if (!changes.isArray()) continue;
            for (JsonNode change : changes) {
                JsonNode messages = change.path("value").path("messages");
                if (!messages.isArray()) continue; // status de entrega não entra no pipeline
                for (JsonNode message : messages) receiveMessage(message);
            }
        }
    }

    private void receiveMessage(JsonNode message) {
        String externalId = text(message, "id"); String from = digits(text(message, "from"));
        String type = text(message, "type"); long timestamp = message.path("timestamp").asLong(0);
        if (externalId == null || from == null || !fresh(timestamp) || !("text".equals(type) || "audio".equals(type))) return;
        String body = "text".equals(type) ? message.path("text").path("body").asText("").trim() : null;
        String mediaId = "audio".equals(type) ? message.path("audio").path("id").asText(null) : null;
        if ((body != null && (body.isBlank() || body.length() > 2_000))
                || (mediaId != null && !mediaId.matches("[A-Za-z0-9._:-]{1,180}")) || (body == null && mediaId == null)) return;
        String phoneHmac = crypto.hmac(from);
        Long usuarioId = linkedUser(phoneHmac);
        if (usuarioId == null && body != null && activateLink(body, from, phoneHmac)) return;
        if (usuarioId == null) return;
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("externalId", externalId); payload.put("fromHmac", phoneHmac); payload.put("type", type);
        if (body != null) payload.put("text", body); else payload.put("mediaId", mediaId);
        payload.put("timestamp", timestamp);
        String minimalPayload = json(payload);
        List<Long> inserted = jdbc.queryForList("""
                insert into assistant_channel_events(usuario_id, channel, external_id, status, payload_hash,
                  payload_ciphertext, payload_key_version, received_at, expires_at)
                values (?, 'WHATSAPP', ?, 'RECEIVED', ?, ?, ?, current_timestamp,
                  current_timestamp + interval '30 days')
                on conflict (external_id) do nothing returning id
                """, Long.class, usuarioId, externalId, crypto.hmac(minimalPayload), crypto.encrypt(minimalPayload), crypto.keyVersion());
        if (inserted.isEmpty()) return;
        long eventId = inserted.get(0);
        jobs.enqueue(JobLane.ASSISTANT, JOB_TYPE + ":" + eventId, JOB_TYPE, json(Map.of("eventId", eventId)),
                (short) 1, 0, Instant.now(clock), 5);
    }

    private boolean activateLink(String body, String from, String phoneHmac) {
        String normalizedCode = body.replace("-", "").trim().toUpperCase();
        if (!normalizedCode.matches("[A-Z2-9]{12}")) return false;
        List<Long> links = jdbc.queryForList("""
                select id from assistant_whatsapp_links
                 where code_hash = ? and used_at is null and expires_at > current_timestamp
                 for update
                """, Long.class, crypto.hmac(normalizedCode));
        if (links.size() != 1) return false;
        return jdbc.update("""
                update assistant_whatsapp_links set wa_ciphertext = ?, wa_key_version = ?, wa_hmac = ?,
                  code_ciphertext = null, idempotency_key = null, used_at = current_timestamp
                 where id = ? and used_at is null
                """, crypto.encrypt(from), crypto.keyVersion(), phoneHmac, links.get(0)) == 1;
    }
    @Scheduled(cron = "${assistant.whatsapp.link-cleanup-cron:0 */15 * * * *}")
    @Transactional
    public void cleanupExpiredLinks() {
        jdbc.update("delete from assistant_whatsapp_links where used_at is null and expires_at <= current_timestamp");
    }
    private Long linkedUser(String hmac) {
        List<Long> users = jdbc.queryForList("select usuario_id from assistant_whatsapp_links where wa_hmac = ? and used_at is not null",
                Long.class, hmac);
        return users.size() == 1 ? users.get(0) : null;
    }
    private boolean fresh(long timestamp) {
        if (timestamp <= 0) return false;
        long delta = Math.abs(Instant.now(clock).getEpochSecond() - timestamp);
        return delta <= 300;
    }
    private String code() {
        StringBuilder value = new StringBuilder(12);
        for (int i = 0; i < 12; i++) value.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        return value.toString();
    }
    private String digits(String value) { return value != null && value.matches("[0-9]{8,20}") ? value : null; }
    private String text(JsonNode node, String field) { String value = node.path(field).asText(null); return value == null || value.isBlank() ? null : value; }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Falha ao serializar evento WhatsApp", failure); }
    }
}
