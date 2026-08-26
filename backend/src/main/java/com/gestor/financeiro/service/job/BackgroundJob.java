package com.gestor.financeiro.service.job;

import java.time.Instant;

public record BackgroundJob(
        long id,
        String key,
        String type,
        String payload,
        short payloadVersion,
        int attempts,
        int maxAttempts,
        Instant leaseUntil
) {
}
