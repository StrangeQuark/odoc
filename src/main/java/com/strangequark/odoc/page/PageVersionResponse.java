package com.strangequark.odoc.page;

import java.time.Instant;
import java.util.UUID;

public record PageVersionResponse(UUID id, int versionNumber, String title, String content, Instant createdAt) {
    static PageVersionResponse from(PageVersion version) {
        return new PageVersionResponse(version.getId(), version.getVersionNumber(), version.getTitle(),
                version.getContent(), version.getCreatedAt());
    }
}
