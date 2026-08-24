package com.strangequark.odoc.encryption;

/**
 * Port for the managed key hierarchy. Implementations may obtain/wrap DEKs through a KMS or HSM,
 * but callers only receive the active scoped/purpose key needed for a single operation.
 */
public interface DataEncryptionKeyProvider {
    DataEncryptionKey activeKey(SecurityScope scope, EncryptionPurpose purpose);

    DataEncryptionKey key(SecurityScope scope, EncryptionPurpose purpose, int version);

    /** Creates a new active DEK and retains the old version for a controlled migration window. */
    default DataEncryptionKey rotate(SecurityScope scope, EncryptionPurpose purpose) {
        throw new UnsupportedOperationException("Key rotation is not available from this provider.");
    }

    /** Prevents any future decrypt/encrypt use of a compromised key version. */
    default void disable(SecurityScope scope, EncryptionPurpose purpose, int version) {
        throw new UnsupportedOperationException("Key disable is not available from this provider.");
    }
}
