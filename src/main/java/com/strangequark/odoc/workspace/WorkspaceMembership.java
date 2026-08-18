package com.strangequark.odoc.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_memberships")
class WorkspaceMembership {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceMembership() {}

    WorkspaceMembership(UUID id, UUID workspaceId, UUID userId, WorkspaceRole role, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID workspaceId() { return workspaceId; }
    UUID userId() { return userId; }
    WorkspaceRole role() { return role; }
    Instant createdAt() { return createdAt; }
}
