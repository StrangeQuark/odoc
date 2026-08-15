package com.strangequark.odoc.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSpaceRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String key,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4_000) String description) {
}
