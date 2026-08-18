package com.strangequark.odoc.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Minimal audit evidence for account and session security actions; it deliberately excludes secrets. */
@Entity
@Table(name = "auth_security_events")
class AuthSecurityEvent {
    @Id private UUID id;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(nullable = false, length = 16) private String outcome;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "origin_lookup_token") private byte[] originLookupToken;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected AuthSecurityEvent() {}

    AuthSecurityEvent(String eventType, String outcome, UUID userId, byte[] originLookupToken, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.outcome = outcome;
        this.userId = userId;
        this.originLookupToken = originLookupToken == null ? null : Arrays.copyOf(originLookupToken, originLookupToken.length);
        this.occurredAt = occurredAt;
    }
}
