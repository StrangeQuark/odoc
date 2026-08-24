package com.strangequark.odoc.page;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(UUID id, UUID spaceId, UUID parentId, UUID authorId, String title, String content,
        String plainText, long revision, Instant createdAt, Instant updatedAt) {
    static PageResponse from(Page page) {
        return new PageResponse(page.getId(), page.getSpaceId(), page.getParentId(), page.getAuthorId(), page.getTitle(), page.getContent(),
                page.getPlainText(), page.getRevision(), page.getCreatedAt(), page.getUpdatedAt());
    }
}
