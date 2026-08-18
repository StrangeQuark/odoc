package com.strangequark.odoc.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Explicit process role used by Compose and Kubernetes deployments. */
@Validated
@ConfigurationProperties("odoc.runtime")
public record OdocRuntimeProperties(@NotNull Mode mode) {
    public enum Mode { API, WORKER, PARSER }
}
