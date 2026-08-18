package com.strangequark.odoc.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
class AuthSession {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true) private byte[] tokenHash;
    @Column(name = "csrf_token_hash", nullable = false) private byte[] csrfTokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AuthSession() {}

    AuthSession(UUID userId, byte[] tokenHash, byte[] csrfTokenHash, Instant expiresAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.csrfTokenHash = Arrays.copyOf(csrfTokenHash, csrfTokenHash.length);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    UUID userId() { return userId; }
    boolean usableAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    boolean hasCsrfHash(byte[] expected) { return java.security.MessageDigest.isEqual(csrfTokenHash, expected); }
    Instant expiresAt() { return expiresAt; }
    void revoke(Instant now) { revokedAt = now; }
}
