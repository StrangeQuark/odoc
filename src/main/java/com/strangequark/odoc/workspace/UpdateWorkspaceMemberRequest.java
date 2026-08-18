package com.strangequark.odoc.workspace;

import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRequest(@NotNull WorkspaceRole role) {}
