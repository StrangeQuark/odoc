package com.strangequark.odoc.encryption;

import java.util.Objects;
import java.util.UUID;

/** Complete authenticated-data context for one bounded encrypted record. */
public record EncryptionContext(
        SecurityScope scope, UUID resourceId, EncryptionPurpose purpose, int plaintextSchemaVersion) {
    public EncryptionContext {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(purpose, "purpose");
        if (plaintextSchemaVersion < 1) {
            throw new IllegalArgumentException("plaintextSchemaVersion must be positive");
        }
    }
}
