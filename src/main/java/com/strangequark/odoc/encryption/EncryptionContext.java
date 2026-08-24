package com.strangequark.odoc.encryption;

import java.util.Objects;
import java.util.UUID;

/** Complete authenticated-data context for one bounded encrypted record. */
public record EncryptionContext(
        SecurityScope scope, UUID resourceId, EncryptionPurpose purpose, int plaintextSchemaVersion,
        String subresource) {
    public EncryptionContext {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(purpose, "purpose");
        if (plaintextSchemaVersion < 1) {
            throw new IllegalArgumentException("plaintextSchemaVersion must be positive");
        }
        subresource = subresource == null ? "" : subresource;
        if (subresource.length() > 160 || !subresource.matches("[A-Za-z0-9:._/-]*")) {
            throw new IllegalArgumentException("subresource is invalid");
        }
    }

    /** Backwards-compatible context for a single authenticated record. */
    public EncryptionContext(
            SecurityScope scope, UUID resourceId, EncryptionPurpose purpose, int plaintextSchemaVersion) {
        this(scope, resourceId, purpose, plaintextSchemaVersion, "");
    }
}
