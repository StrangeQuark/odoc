package com.strangequark.odoc.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Column(name = "plain_text", nullable = false)
    private String plainText;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long revision;

    protected Page() {
    }

    Page(UUID id, UUID spaceId, UUID parentId, String title, String content, Instant createdAt) {
        this(id, spaceId, parentId, title, content, PageContentText.from(content), null, createdAt);
    }

    Page(UUID id, UUID spaceId, UUID parentId, String title, String content, String plainText, Instant createdAt) {
        this(id, spaceId, parentId, title, content, plainText, null, createdAt);
    }

    Page(UUID id, UUID spaceId, UUID parentId, String title, String content, String plainText, UUID authorId, Instant createdAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.parentId = parentId;
        this.title = title;
        this.content = content;
        this.plainText = plainText;
        this.authorId = authorId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getSpaceId() { return spaceId; }
    UUID getParentId() { return parentId; }
    String getTitle() { return title; }
    String getContent() { return content; }
    String getPlainText() { return plainText; }
    UUID getAuthorId() { return authorId; }
    boolean isArchived() { return archivedAt != null; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }

    long getRevision() { return revision; }

    void update(String title, String content, String plainText, Instant now) {
        this.title = title;
        this.content = content;
        this.plainText = plainText;
        this.updatedAt = now;
    }

    void moveTo(UUID parentId, Instant now) {
        this.parentId = parentId;
        this.updatedAt = now;
    }

    void archive(Instant now) {
        this.archivedAt = now;
        this.updatedAt = now;
    }
}
