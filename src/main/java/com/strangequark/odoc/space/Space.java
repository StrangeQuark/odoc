package com.strangequark.odoc.space;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spaces")
class Space {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "space_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    protected Space() {
    }

    Space(UUID id, UUID workspaceId, String key, String name, String description, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getWorkspaceId() { return workspaceId; }
    String getKey() { return key; }
    String getName() { return name; }
    String getDescription() { return description; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }

    void update(String name, String description, Instant now) {
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }
}
