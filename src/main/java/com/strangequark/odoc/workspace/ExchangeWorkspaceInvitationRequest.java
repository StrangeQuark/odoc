package com.strangequark.odoc.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExchangeWorkspaceInvitationRequest(@NotBlank @Size(max = 256) String verifier) {}
