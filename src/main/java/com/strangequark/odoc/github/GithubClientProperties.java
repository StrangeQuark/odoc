package com.strangequark.odoc.github;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public GitHub endpoint configuration; App credentials arrive in the later GitHub-App package. */
@Validated
@ConfigurationProperties("odoc.github")
record GithubClientProperties(@NotNull URI apiBaseUrl) {}
