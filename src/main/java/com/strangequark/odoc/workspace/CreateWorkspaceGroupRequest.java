package com.strangequark.odoc.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceGroupRequest(@NotBlank @Size(max = 160) String name) {}
