package com.gestor.financeiro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rate_limit_buckets")
public class RateLimitBucket {

    @Id
    @Column(name = "rate_key", length = 256, nullable = false)
    private String rateKey;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    public RateLimitBucket(String rateKey, Instant windowStart, int attemptCount) {
        this.rateKey = rateKey;
        this.windowStart = windowStart;
        this.attemptCount = attemptCount;
    }
}
