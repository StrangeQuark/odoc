package com.strangequark.odoc.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceInvitationResponse(UUID id, UUID routeId, String email, Instant expiresAt, Instant createdAt) {}
