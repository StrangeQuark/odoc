package com.strangequark.odoc.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deliberately small HTTP-boundary policy. Browser clients normally use the
 * frontend's same-origin {@code /api} proxy; cross-origin browser access is an
 * opt-in deployment decision rather than an accidental CORS default.
 */
@ConfigurationProperties("odoc.security")
public record OdocSecurityProperties(List<String> allowedOrigins, boolean rejectForwardedHeaders) {
    public OdocSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    boolean allows(String origin) {
        return allowedOrigins.contains(origin);
    }
}
