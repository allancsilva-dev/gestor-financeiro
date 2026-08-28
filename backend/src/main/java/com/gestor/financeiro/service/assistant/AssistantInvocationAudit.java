package com.gestor.financeiro.service.assistant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssistantInvocationAudit {
    private final JdbcTemplate jdbc;
    public AssistantInvocationAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void record(Long usuarioId, Long conversationId, String provider, String model, String result) {
        jdbc.update("""
                insert into assistant_invocations(usuario_id, conversation_id, provider, model, operation,
                  result, prompt_version, schema_version, created_at)
                values (?,?,?,?, 'EXTRACT', ?, 'financial-extract-v1', ?, current_timestamp)
                """, usuarioId, conversationId, provider, model, result, TransactionDraftSchema.VERSION);
    }
}
