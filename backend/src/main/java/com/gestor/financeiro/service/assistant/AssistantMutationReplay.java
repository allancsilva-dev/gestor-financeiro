package com.gestor.financeiro.service.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.exception.AssistantException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AssistantMutationReplay {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AssistantMutationReplay(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public <T> Optional<T> find(Long usuarioId, String key, String requestHash, Class<T> responseType) {
        var rows = jdbc.query("""
                select request_hash, response_json from assistant_invocations
                 where usuario_id = ? and idempotency_key = ?
                   and (expires_at is null or expires_at > current_timestamp)
                """, (rs, row) -> new Replay(rs.getString(1), rs.getString(2)), usuarioId, key);
        if (rows.isEmpty()) return Optional.empty();
        Replay replay = rows.get(0);
        if (!requestHash.equals(replay.requestHash())) throw new AssistantException(
                "DRAFT_CONFLICT", "Idempotency-Key reutilizada com payload diferente", HttpStatus.CONFLICT);
        try {
            return Optional.of(objectMapper.readValue(replay.responseJson(), responseType));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Replay de mutação inválido", e);
        }
    }

    public void store(Long usuarioId, Long conversationId, String operation, String key,
                      String requestHash, Object response) {
        try {
            jdbc.update("""
                    insert into assistant_invocations(usuario_id, conversation_id, provider, model, operation,
                      result, prompt_version, schema_version, idempotency_key, request_hash, response_json,
                      created_at, expires_at)
                    values (?, ?, 'INTERNAL', 'DETERMINISTIC', ?, 'SUCCESS',
                      'internal-v1', ?, ?, ?, ?, ?, ?)
                    """, usuarioId, conversationId, operation, TransactionDraftSchema.VERSION, key,
                    requestHash, objectMapper.writeValueAsString(response), LocalDateTime.now(clock),
                    LocalDateTime.now(clock).plusHours(24));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao persistir replay de mutação", e);
        }
    }

    public void lockUser(Long usuarioId) {
        Long locked = jdbc.queryForObject("select id from usuarios where id = ? for update", Long.class, usuarioId);
        if (locked == null) throw new com.gestor.financeiro.exception.ResourceNotFoundException("Usuário não encontrado");
    }

    @Scheduled(cron = "${assistant.replay-cleanup-cron:0 20 * * * *}")
    @Transactional
    public void cleanupExpired() {
        jdbc.update("delete from assistant_invocations where expires_at is not null and expires_at <= current_timestamp");
    }

    private record Replay(String requestHash, String responseJson) { }
}
