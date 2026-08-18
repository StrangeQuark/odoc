package com.strangequark.odoc.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
class Workspace {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "security_scope_id", nullable = false, unique = true)
    private UUID securityScopeId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WorkspaceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    protected Workspace() {}

    Workspace(UUID id, String name, Instant createdAt) {
        this(id, name, id, createdAt);
    }

    Workspace(UUID id, String name, UUID securityScopeId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.securityScopeId = securityScopeId;
        this.status = WorkspaceStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    String name() { return name; }
    UUID securityScopeId() { return securityScopeId; }
    WorkspaceStatus status() { return status; }
    boolean active() { return status == WorkspaceStatus.ACTIVE; }
    long revision() { return revision; }
    void rename(String nextName, Instant now) {
        this.name = nextName;
        this.updatedAt = now;
    }
    void suspend() { status = WorkspaceStatus.SUSPENDED; }
    void restore() { status = WorkspaceStatus.ACTIVE; }
}
