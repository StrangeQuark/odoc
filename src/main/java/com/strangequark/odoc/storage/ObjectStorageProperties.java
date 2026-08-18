package com.strangequark.odoc.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit S3-compatible configuration; production defaults fail closed. */
@ConfigurationProperties("odoc.object-storage")
public record ObjectStorageProperties(
        boolean enabled,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean requireTls) {
}
