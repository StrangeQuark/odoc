package com.strangequark.odoc.commentary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePageCommentRequest(@NotBlank @Size(max = 10_000) String body, UUID parentId) { }
