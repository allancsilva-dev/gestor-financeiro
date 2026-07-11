package com.gestor.financeiro.service;

import com.gestor.financeiro.model.RateLimitBucket;
import com.gestor.financeiro.repository.RateLimitBucketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final int MAX_INSERT_RACE_RETRIES = 2;

    private final RateLimitBucketRepository bucketRepository;
    private final TransactionTemplate transactionTemplate;

    public RateLimitService(RateLimitBucketRepository bucketRepository,
                            PlatformTransactionManager transactionManager) {
        this.bucketRepository = bucketRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public RateLimitDecision consume(String key, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitDecision.allowed(0);
        }

        for (int attempt = 0; attempt <= MAX_INSERT_RACE_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> consumeInTransaction(key, limit, window));
            } catch (DataIntegrityViolationException duplicateInsertRace) {
                if (attempt == MAX_INSERT_RACE_RETRIES) {
                    throw duplicateInsertRace;
                }
            }
        }

        throw new IllegalStateException("Falha ao registrar rate limit");
    }

    private RateLimitDecision consumeInTransaction(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Instant expiresAt;

        RateLimitBucket bucket = bucketRepository.findByRateKeyForUpdate(key).orElse(null);
        if (bucket == null) {
            bucketRepository.saveAndFlush(new RateLimitBucket(key, now, 1));
            return RateLimitDecision.allowed(limit - 1);
        }

        expiresAt = bucket.getWindowStart().plus(window);
        if (!expiresAt.isAfter(now)) {
            bucket.setWindowStart(now);
            bucket.setAttemptCount(1);
            return RateLimitDecision.allowed(limit - 1);
        }

        if (bucket.getAttemptCount() >= limit) {
            long retryAfterMillis = Duration.between(now, expiresAt).toMillis();
            long retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
            return RateLimitDecision.blocked(retryAfterSeconds);
        }

        bucket.setAttemptCount(bucket.getAttemptCount() + 1);
        return RateLimitDecision.allowed(limit - bucket.getAttemptCount());
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cleanupExpiredEntries() {
        int removed = bucketRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofMinutes(5)));
        if (removed > 0) {
            log.debug("Rate limit cleanup: {} expired buckets removed", removed);
        }
    }

    public record RateLimitDecision(boolean allowed, int remaining, long retryAfterSeconds) {
        static RateLimitDecision allowed(int remaining) {
            return new RateLimitDecision(true, Math.max(0, remaining), 0);
        }

        static RateLimitDecision blocked(long retryAfterSeconds) {
            return new RateLimitDecision(false, 0, retryAfterSeconds);
        }
    }
}
