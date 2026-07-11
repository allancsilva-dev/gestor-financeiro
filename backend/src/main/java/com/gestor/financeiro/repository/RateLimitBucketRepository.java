package com.gestor.financeiro.repository;

import com.gestor.financeiro.model.RateLimitBucket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RateLimitBucketRepository extends JpaRepository<RateLimitBucket, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from RateLimitBucket b where b.rateKey = :rateKey")
    Optional<RateLimitBucket> findByRateKeyForUpdate(@Param("rateKey") String rateKey);

    @Modifying
    @Query("delete from RateLimitBucket b where b.windowStart < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
