package com.strangequark.odoc.auth;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime controls for local-account/session authentication. */
@ConfigurationProperties("odoc.auth")
public record OdocAuthProperties(
        boolean localRegistrationEnabled, UUID instanceScopeId, Duration sessionTtl, boolean secureCookies) {
    public OdocAuthProperties {
        if (instanceScopeId == null) throw new IllegalArgumentException("instanceScopeId is required");
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
    }
}
