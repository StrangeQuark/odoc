package com.strangequark.odoc.workspace;

import java.time.Instant;
import java.util.UUID;

/** A member returned only to an owner of the same workspace. */
public record WorkspaceMemberResponse(UUID id, UUID userId, String email, WorkspaceRole role, Instant joinedAt) {}
