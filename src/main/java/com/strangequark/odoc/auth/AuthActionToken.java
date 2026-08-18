package com.strangequark.odoc.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Stores only a hash of a short-lived account action verifier. */
@Entity
@Table(name = "auth_action_tokens")
class AuthActionToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32) private AuthActionType actionType;
    @Column(name = "token_hash", nullable = false, unique = true) private byte[] tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AuthActionToken() {}

    AuthActionToken(UUID userId, AuthActionType actionType, byte[] tokenHash, Instant expiresAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.actionType = actionType;
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    UUID userId() { return userId; }
    boolean isUsable(AuthActionType expectedType, Instant now) {
        return actionType == expectedType && consumedAt == null && expiresAt.isAfter(now);
    }
    void consume(Instant now) { consumedAt = now; }
}
