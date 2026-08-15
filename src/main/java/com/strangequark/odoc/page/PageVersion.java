package com.strangequark.odoc.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "page_versions")
class PageVersion {
    @Id
    private UUID id;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PageVersion() {
    }

    PageVersion(UUID id, UUID pageId, int versionNumber, String title, String content, Instant createdAt) {
        this.id = id;
        this.pageId = pageId;
        this.versionNumber = versionNumber;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getPageId() { return pageId; }
    int getVersionNumber() { return versionNumber; }
    String getTitle() { return title; }
    String getContent() { return content; }
    Instant getCreatedAt() { return createdAt; }
}
