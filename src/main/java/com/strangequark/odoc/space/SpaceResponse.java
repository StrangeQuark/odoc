package com.strangequark.odoc.space;

import java.time.Instant;
import java.util.UUID;

public record SpaceResponse(
        UUID id, UUID workspaceId, String key, String name, String description, Instant createdAt, Instant updatedAt) {
    static SpaceResponse from(Space space) {
        return new SpaceResponse(space.getId(), space.getWorkspaceId(), space.getKey(), space.getName(), space.getDescription(),
                space.getCreatedAt(), space.getUpdatedAt());
    }
}
