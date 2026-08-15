package com.strangequark.odoc.commentary;

import java.time.Instant;
import java.util.UUID;

public record PageCommentResponse(UUID id, UUID parentId, String author, String body, Instant createdAt) {
    static PageCommentResponse from(PageComment comment) {
        return new PageCommentResponse(comment.getId(), comment.getParentId(), comment.getAuthor(), comment.getBody(), comment.getCreatedAt());
    }
}
