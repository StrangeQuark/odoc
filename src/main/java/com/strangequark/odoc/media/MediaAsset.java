package com.strangequark.odoc.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
class MediaAsset {
    @Id private UUID id;
    @Column(nullable = false) private UUID spaceId;
    @Column(nullable = false) private String filename;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private byte[] content;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private Instant createdAt;

    protected MediaAsset() {}

    MediaAsset(UUID id, UUID spaceId, String filename, String contentType, byte[] content, Instant createdAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
        this.sizeBytes = content.length;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID spaceId() { return spaceId; }
    String filename() { return filename; }
    String contentType() { return contentType; }
    byte[] content() { return content; }
    long sizeBytes() { return sizeBytes; }
    Instant createdAt() { return createdAt; }
}
