package com.strangequark.odoc.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceGroupResponse(UUID id, String name, WorkspaceGroupStatus status, long revision, Instant createdAt) {}
