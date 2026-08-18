package com.strangequark.odoc.encryption;

/**
 * Port for the managed key hierarchy. Implementations may obtain/wrap DEKs through a KMS or HSM,
 * but callers only receive the active scoped/purpose key needed for a single operation.
 */
public interface DataEncryptionKeyProvider {
    DataEncryptionKey activeKey(SecurityScope scope, EncryptionPurpose purpose);

    DataEncryptionKey key(SecurityScope scope, EncryptionPurpose purpose, int version);
}
