package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.AssistantDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssistantDraftRepository extends JpaRepository<AssistantDraft, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from AssistantDraft d where d.id = :id and d.usuario.id = :usuarioId")
    Optional<AssistantDraft> findOwnedForUpdate(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Query(value = """
            select * from assistant_drafts
             where conversation_id = :conversationId and usuario_id = :usuarioId
               and status = 'PENDING' and question_count = 1 and expires_at > :now
             order by created_at desc limit 1 for update
            """, nativeQuery = true)
    Optional<AssistantDraft> findLatestClarificationForUpdate(
            @Param("conversationId") Long conversationId, @Param("usuarioId") Long usuarioId,
            @Param("now") java.time.LocalDateTime now);
}
