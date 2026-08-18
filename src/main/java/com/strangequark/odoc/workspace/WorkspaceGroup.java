package com.strangequark.odoc.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** A workspace-local group. Its display name is stored only as an encrypted record envelope. */
@Entity
@Table(name = "workspace_groups", uniqueConstraints = @UniqueConstraint(
        name = "workspace_groups_workspace_name_unique", columnNames = {"workspace_id", "name_lookup_token"}))
class WorkspaceGroup {
    @Id private UUID id;
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "name_lookup_token", nullable = false) private byte[] nameLookupToken;
    @Column(name = "name_envelope", nullable = false) private String nameEnvelope;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private WorkspaceGroupStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long revision;

    protected WorkspaceGroup() {}

    WorkspaceGroup(UUID id, UUID workspaceId, byte[] nameLookupToken, String nameEnvelope, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.nameLookupToken = Arrays.copyOf(nameLookupToken, nameLookupToken.length);
        this.nameEnvelope = nameEnvelope;
        this.status = WorkspaceGroupStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    UUID workspaceId() { return workspaceId; }
    String nameEnvelope() { return nameEnvelope; }
    boolean active() { return status == WorkspaceGroupStatus.ACTIVE; }
    WorkspaceGroupStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long revision() { return revision; }
    void suspend() { status = WorkspaceGroupStatus.SUSPENDED; }
    void restore() { status = WorkspaceGroupStatus.ACTIVE; }
}
