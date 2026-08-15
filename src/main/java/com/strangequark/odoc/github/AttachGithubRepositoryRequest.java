package com.strangequark.odoc.github;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record AttachGithubRepositoryRequest(
        @NotBlank
        @Pattern(regexp = "https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?", message = "Use a canonical public GitHub repository URL.")
        String url) {}
