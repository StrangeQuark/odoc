package com.strangequark.odoc.page;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(UUID id, UUID spaceId, UUID parentId, String title, String content, Instant createdAt, Instant updatedAt) {
    static PageResponse from(Page page) {
        return new PageResponse(page.getId(), page.getSpaceId(), page.getParentId(), page.getTitle(), page.getContent(),
                page.getCreatedAt(), page.getUpdatedAt());
    }
}
