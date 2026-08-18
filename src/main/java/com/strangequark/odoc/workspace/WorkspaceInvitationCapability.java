package com.strangequark.odoc.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** A short-lived, opaque browser capability issued only after an invitation verifier is exchanged. */
@Entity
@Table(name = "workspace_invitation_capabilities")
class WorkspaceInvitationCapability {
    @Id private UUID id;
    @Column(name = "invitation_id", nullable = false) private UUID invitationId;
    @Column(name = "token_hash", nullable = false, unique = true) private byte[] tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorkspaceInvitationCapability() {}

    WorkspaceInvitationCapability(UUID id, UUID invitationId, byte[] tokenHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.invitationId = invitationId;
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    UUID invitationId() { return invitationId; }
    boolean usableAt(Instant now) { return expiresAt.isAfter(now); }
    Instant expiresAt() { return expiresAt; }
}
