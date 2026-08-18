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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    protected Workspace() {}

    Workspace(UUID id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID id() { return id; }
    String name() { return name; }
}
