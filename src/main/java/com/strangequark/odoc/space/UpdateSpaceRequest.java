package com.strangequark.odoc.space;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The small editable portion of a space; its stable key is intentionally immutable. */
public record UpdateSpaceRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4_000) String description) {
}
