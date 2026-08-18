package com.strangequark.odoc.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitations")
class WorkspaceInvitation {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "email_lookup_token", nullable = false) private byte[] emailLookupToken;
    @Column(name = "email_envelope", nullable = false) private String emailEnvelope;
    @Column(name = "token_hash", nullable = false, unique = true) private byte[] tokenHash;
    @Column(name = "route_id", nullable = false, unique = true) private UUID routeId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorkspaceInvitation() {}

    WorkspaceInvitation(UUID id, UUID workspaceId, byte[] emailLookupToken, String emailEnvelope, byte[] tokenHash,
            Instant expiresAt, Instant createdAt) {
        this(id, workspaceId, emailLookupToken, emailEnvelope, tokenHash, UUID.randomUUID(), expiresAt, createdAt);
    }

    WorkspaceInvitation(UUID id, UUID workspaceId, byte[] emailLookupToken, String emailEnvelope, byte[] tokenHash,
            UUID routeId, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.emailLookupToken = Arrays.copyOf(emailLookupToken, emailLookupToken.length);
        this.emailEnvelope = emailEnvelope;
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.routeId = routeId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID workspaceId() { return workspaceId; }
    UUID routeId() { return routeId; }
    byte[] tokenHash() { return Arrays.copyOf(tokenHash, tokenHash.length); }
    String emailEnvelope() { return emailEnvelope; }
    boolean usableAt(Instant now) { return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now); }
    boolean recipientCanRetryAt(Instant now) { return acceptedAt != null && revokedAt == null && expiresAt.isAfter(now); }
    Instant expiresAt() { return expiresAt; }
    Instant createdAt() { return createdAt; }
    void accept(Instant now) { acceptedAt = now; }
    void revoke(Instant now) { revokedAt = now; }
}
