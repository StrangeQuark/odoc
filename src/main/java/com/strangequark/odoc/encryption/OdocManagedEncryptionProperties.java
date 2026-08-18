package com.strangequark.odoc.encryption;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local development configuration for the managed-envelope adapter. */
@ConfigurationProperties("odoc.encryption.managed")
public record OdocManagedEncryptionProperties(boolean enabled, String wrappingKeyBase64) {
}
