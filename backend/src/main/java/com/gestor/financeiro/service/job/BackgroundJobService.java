package com.gestor.financeiro.service.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestor.financeiro.exception.FinancialConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class BackgroundJobService {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Z0-9._:-]{1,120}");
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BackgroundJobService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long enqueue(String key, String type, String payload, short payloadVersion,
                        int priority, Instant availableAt, int maxAttempts) {
        requireIdentifier(key, "job key", 180);
        requireIdentifier(type, "job type", 80);
        validatePayload(payload);
        if (payloadVersion < 1 || priority < -100 || priority > 100 || maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("Parâmetros de job fora dos limites");
        }

        List<Long> inserted = jdbcTemplate.queryForList("""
                insert into background_jobs
                    (job_key, job_type, payload, payload_version, priority, available_at, max_attempts)
                values (?, ?, cast(? as jsonb), ?, ?, ?, ?)
                on conflict (job_key) do nothing
                returning id
                """, Long.class, key, type, payload, payloadVersion, priority,
                Timestamp.from(Objects.requireNonNullElseGet(availableAt, Instant::now)), maxAttempts);
        if (!inserted.isEmpty()) {
            return inserted.get(0);
        }

        List<Long> matching = jdbcTemplate.queryForList("""
                select id from background_jobs
                where job_key = ? and job_type = ? and payload = cast(? as jsonb) and payload_version = ?
                """, Long.class, key, type, payload, payloadVersion);
        if (matching.isEmpty()) {
            throw new FinancialConflictException("Job key já usada com conteúdo diferente");
        }
        return matching.get(0);
    }

    @Transactional
    public List<BackgroundJob> claim(String workerId, int limit, Duration leaseDuration) {
        requireIdentifier(workerId, "worker id", 100);
        if (limit < 1 || limit > 200 || leaseDuration.isNegative() || leaseDuration.isZero()
                || leaseDuration.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Limite ou lease inválido");
        }

        jdbcTemplate.update("""
                update background_jobs
                   set status = 'DEAD_LETTER', lease_owner = null, lease_until = null,
                       finished_at = current_timestamp, updated_at = current_timestamp,
                       last_error = 'LEASE_EXPIRED_AFTER_MAX_ATTEMPTS'
                 where status = 'RUNNING' and lease_until < current_timestamp and attempts >= max_attempts
                """);

        return jdbcTemplate.query("""
                with candidates as (
                    select id
                    from background_jobs
                    where attempts < max_attempts
                      and available_at <= current_timestamp
                      and (status in ('PENDING', 'RETRY')
                           or (status = 'RUNNING' and lease_until < current_timestamp))
                    order by priority desc, available_at, id
                    for update skip locked
                    limit ?
                )
                update background_jobs j
                   set status = 'RUNNING',
                       lease_owner = ?,
                       lease_until = current_timestamp + (? * interval '1 second'),
                       attempts = attempts + 1,
                       updated_at = current_timestamp
                  from candidates c
                 where j.id = c.id
                returning j.id, j.job_key, j.job_type, j.payload::text, j.payload_version,
                          j.attempts, j.max_attempts, j.lease_until
                """, (rs, rowNum) -> new BackgroundJob(
                rs.getLong("id"),
                rs.getString("job_key"),
                rs.getString("job_type"),
                rs.getString("payload"),
                rs.getShort("payload_version"),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getTimestamp("lease_until").toInstant()
        ), limit, workerId, leaseDuration.toSeconds());
    }

    @Transactional
    public boolean renewLease(long jobId, String workerId, Duration leaseDuration) {
        requireIdentifier(workerId, "worker id", 100);
        if (leaseDuration.isNegative() || leaseDuration.isZero() || leaseDuration.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Lease inválido");
        }
        return jdbcTemplate.update("""
                update background_jobs
                   set lease_until = current_timestamp + (? * interval '1 second'), updated_at = current_timestamp
                 where id = ? and status = 'RUNNING' and lease_owner = ? and lease_until >= current_timestamp
                """, leaseDuration.toSeconds(), jobId, workerId) == 1;
    }

    @Transactional
    public boolean complete(long jobId, String workerId) {
        requireIdentifier(workerId, "worker id", 100);
        return jdbcTemplate.update("""
                update background_jobs
                   set status = 'COMPLETED', lease_owner = null, lease_until = null,
                       finished_at = current_timestamp, updated_at = current_timestamp, last_error = null
                 where id = ? and status = 'RUNNING' and lease_owner = ?
                """, jobId, workerId) == 1;
    }

    @Transactional
    public boolean fail(long jobId, String workerId, String errorCode, Duration retryDelay) {
        requireIdentifier(workerId, "worker id", 100);
        if (retryDelay.isNegative() || retryDelay.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Retry delay inválido");
        }
        if (errorCode == null || !SAFE_ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("Código de erro inválido");
        }
        return jdbcTemplate.update("""
                update background_jobs
                   set status = case when attempts >= max_attempts then 'DEAD_LETTER' else 'RETRY' end,
                       lease_owner = null,
                       lease_until = null,
                       available_at = current_timestamp + (? * interval '1 second'),
                       finished_at = case when attempts >= max_attempts then current_timestamp else null end,
                       last_error = ?,
                       updated_at = current_timestamp
                 where id = ? and status = 'RUNNING' and lease_owner = ?
                """, retryDelay.toSeconds(), errorCode, jobId, workerId) == 1;
    }

    private void validatePayload(String payload) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload de job ausente ou maior que 64 KiB");
        }
        try {
            objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload de job não é JSON válido", exception);
        }
    }

    private static void requireIdentifier(String value, String field, int maxLength) {
        if (value == null || value.length() > maxLength || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " inválido");
        }
    }
}
