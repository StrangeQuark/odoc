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
    @Column private byte[] content;
    @Column(name = "object_key", length = 512) private String objectKey;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "storage_state", nullable = false, length = 24) private MediaStorageState storageState;
    @Column(name = "content_sha256", length = 64) private String contentSha256;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private Instant createdAt;

    protected MediaAsset() {}

    MediaAsset(UUID id, UUID spaceId, String filename, String contentType, byte[] content, Instant createdAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
        this.storageState = MediaStorageState.AVAILABLE;
        this.sizeBytes = content.length;
        this.createdAt = createdAt;
    }

    MediaAsset(UUID id, UUID spaceId, String filename, String contentType, String objectKey,
            String contentSha256, long sizeBytes, Instant createdAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.filename = filename;
        this.contentType = contentType;
        this.objectKey = objectKey;
        this.storageState = MediaStorageState.AVAILABLE;
        this.contentSha256 = contentSha256;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID spaceId() { return spaceId; }
    String filename() { return filename; }
    String contentType() { return contentType; }
    byte[] content() { return content; }
    String objectKey() { return objectKey; }
    String contentSha256() { return contentSha256; }
    boolean storedExternally() { return objectKey != null; }
    boolean available() { return storageState == MediaStorageState.AVAILABLE; }
    void markDeletionPending() { storageState = MediaStorageState.DELETE_PENDING; }
    long sizeBytes() { return sizeBytes; }
    Instant createdAt() { return createdAt; }
}
