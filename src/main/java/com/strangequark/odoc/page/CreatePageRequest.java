package com.strangequark.odoc.page;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePageRequest(@NotBlank @Size(max = 240) String title, @Size(max = 200_000) String content, UUID parentId) {
}
