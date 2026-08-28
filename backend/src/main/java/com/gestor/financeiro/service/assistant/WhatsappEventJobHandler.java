package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.dto.AssistantDtos.MessageRequest;
import com.gestor.financeiro.service.job.BackgroundJob;
import com.gestor.financeiro.service.job.JobHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Component
public class WhatsappEventJobHandler implements JobHandler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final WhatsappCrypto crypto;
    private final AssistantService assistant;
    private final MetaWhatsappClient meta;
    private final AssistantAudioService audio;
    private final long maxAudioBytes;

    public WhatsappEventJobHandler(JdbcTemplate jdbc, ObjectMapper mapper, WhatsappCrypto crypto,
                                   AssistantService assistant, MetaWhatsappClient meta, AssistantAudioService audio,
                                   @Value("${assistant.limits.audio-bytes:8388608}") long maxAudioBytes) {
        this.jdbc = jdbc; this.mapper = mapper; this.crypto = crypto; this.assistant = assistant; this.meta = meta;
        this.audio = audio; this.maxAudioBytes = maxAudioBytes;
    }
    @Override public String type() { return WhatsappService.JOB_TYPE; }
    @Override public void handle(BackgroundJob job) throws Exception {
        long eventId = mapper.readTree(job.payload()).path("eventId").asLong();
        if (eventId <= 0) throw new IllegalArgumentException("Job sem eventId");
        List<Event> events = jdbc.query("""
                select e.usuario_id, e.status, e.payload_ciphertext
                  from assistant_channel_events e where e.id = ?
                """, (rs, row) -> new Event(rs.getLong(1), rs.getString(2), rs.getString(3)), eventId);
        if (events.size() != 1) throw new IllegalArgumentException("Evento inexistente");
        Event event = events.get(0); if ("PROCESSED".equals(event.status())) return;
        JsonNode payload = mapper.readTree(crypto.decrypt(event.ciphertext()));
        String hmac = payload.path("fromHmac").asText();
        List<Link> links = jdbc.query("""
                select wa_ciphertext, conversation_id from assistant_whatsapp_links
                 where usuario_id = ? and wa_hmac = ? and used_at is not null
                """, (rs, row) -> new Link(rs.getString(1), (Long) rs.getObject(2)), event.usuarioId(), hmac);
        if (links.size() != 1) { jdbc.update("update assistant_channel_events set status = 'DISCARDED', processed_at = current_timestamp where id = ?", eventId); return; }
        Link link = links.get(0);
        boolean audioMessage = "audio".equals(payload.path("type").asText());
        String input;
        if (audioMessage) {
            var media = meta.downloadAudio(payload.path("mediaId").asText(), maxAudioBytes);
            input = audio.transcribeFile(event.usuarioId(), media.path(), media.contentType());
        } else input = payload.path("text").asText();
        var response = assistant.receive(event.usuarioId(), new MessageRequest(link.conversationId(), input));
        if (link.conversationId() == null) jdbc.update("update assistant_whatsapp_links set conversation_id = ? where usuario_id = ? and wa_hmac = ?",
                response.conversationId(), event.usuarioId(), hmac);
        String reply = audioMessage ? "Transcrição: “" + input + "”\n\n" + response.reply() : response.reply();
        meta.sendText(crypto.decrypt(link.phoneCiphertext()), reply.substring(0, Math.min(reply.length(), 4_000)));
        jdbc.update("update assistant_channel_events set status = 'PROCESSED', processed_at = current_timestamp where id = ?", eventId);
    }
    private record Event(Long usuarioId, String status, String ciphertext) { }
    private record Link(String phoneCiphertext, Long conversationId) { }
}
