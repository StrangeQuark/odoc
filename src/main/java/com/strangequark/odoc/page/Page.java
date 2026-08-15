package com.strangequark.odoc.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pages")
class Page {
    @Id
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Page() {
    }

    Page(UUID id, UUID spaceId, UUID parentId, String title, String content, Instant createdAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.parentId = parentId;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getSpaceId() { return spaceId; }
    UUID getParentId() { return parentId; }
    String getTitle() { return title; }
    String getContent() { return content; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }

    void update(String title, String content, Instant now) {
        this.title = title;
        this.content = content;
        this.updatedAt = now;
    }
}
