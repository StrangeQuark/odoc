package com.strangequark.odoc.github;

import jakarta.validation.constraints.NotBlank;

record AttachGithubRepositoryRequest(
        @NotBlank
        String url) {}
