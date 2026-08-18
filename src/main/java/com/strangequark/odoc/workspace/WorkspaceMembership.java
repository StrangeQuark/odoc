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
public class WorkspaceMembership {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceMembershipStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceMembership() {}

    WorkspaceMembership(UUID id, UUID workspaceId, UUID userId, WorkspaceRole role, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.status = WorkspaceMembershipStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    public UUID userId() { return userId; }
    public WorkspaceRole role() { return role; }
    WorkspaceMembershipStatus status() { return status; }
    public boolean active() { return status == WorkspaceMembershipStatus.ACTIVE; }
    public Instant createdAt() { return createdAt; }
    void changeRole(WorkspaceRole role) { this.role = role; }
    void suspend() { this.status = WorkspaceMembershipStatus.SUSPENDED; }
    void restore() { this.status = WorkspaceMembershipStatus.ACTIVE; }
}
