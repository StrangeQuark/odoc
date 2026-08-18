package com.strangequark.odoc.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceGroupMemberResponse(UUID userId, String email, Instant joinedAt) {}
