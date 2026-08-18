package com.strangequark.odoc.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Opaque, shared counters for authentication abuse controls. No email or network address is stored. */
@Entity
@Table(name = "auth_rate_limit_buckets")
class AuthRateLimitBucket {
    @Id
    @Column(name = "bucket_key", nullable = false, length = 128)
    private String bucketKey;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthRateLimitBucket() {}

    AuthRateLimitBucket(String bucketKey, Instant now) {
        this.bucketKey = bucketKey;
        this.windowStartedAt = now;
        this.updatedAt = now;
    }

    boolean blockedAt(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    void recordFailure(Instant now, int threshold, java.time.Duration window, java.time.Duration block) {
        if (!windowStartedAt.plus(window).isAfter(now)) {
            windowStartedAt = now;
            attempts = 0;
            blockedUntil = null;
        }
        attempts++;
        if (attempts >= threshold) blockedUntil = now.plus(block);
        updatedAt = now;
    }

    void recordSuccess(Instant now) {
        attempts = 0;
        blockedUntil = null;
        windowStartedAt = now;
        updatedAt = now;
    }
}
