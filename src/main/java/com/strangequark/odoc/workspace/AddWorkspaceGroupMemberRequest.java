package com.strangequark.odoc.workspace;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddWorkspaceGroupMemberRequest(@NotNull UUID userId) {}
