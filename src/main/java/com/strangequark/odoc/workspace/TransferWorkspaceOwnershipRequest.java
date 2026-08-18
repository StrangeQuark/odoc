package com.strangequark.odoc.workspace;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferWorkspaceOwnershipRequest(@NotNull UUID successorUserId) {}
