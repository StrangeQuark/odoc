package com.strangequark.odoc.page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Small navigation projection; the document body stays available from the page endpoint. */
public record PageTreeNode(UUID id, UUID parentId, String title, long revision,
        Instant updatedAt, List<PageTreeNode> children) {}
