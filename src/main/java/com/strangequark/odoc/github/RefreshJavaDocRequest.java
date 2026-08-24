package com.strangequark.odoc.github;

import jakarta.validation.constraints.NotBlank;

record RefreshJavaDocRequest(@NotBlank String sourcePath) {}
