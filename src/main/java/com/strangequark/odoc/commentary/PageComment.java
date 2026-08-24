package com.strangequark.odoc.commentary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "page_comments")
class PageComment {
    @Id private UUID id;
    @Column(name = "page_id", nullable = false) private UUID pageId;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "author_id") private UUID authorId;
    @Column(nullable = false, length = 120) private String author;
    @Column(nullable = false) private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected PageComment() { }
    PageComment(UUID id, UUID pageId, UUID parentId, UUID authorId, String author, String body, Instant createdAt) {
        this.id = id; this.pageId = pageId; this.parentId = parentId; this.authorId = authorId;
        this.author = author; this.body = body; this.createdAt = createdAt;
    }
    UUID getId() { return id; } UUID getPageId() { return pageId; } UUID getParentId() { return parentId; }
    UUID getAuthorId() { return authorId; } String getAuthor() { return author; }
    String getBody() { return body; } Instant getCreatedAt() { return createdAt; }
}
