package com.strangequark.odoc.encryption;

import javax.crypto.SecretKey;

/** An active or retained 256-bit AES data-encryption key. */
public record DataEncryptionKey(int version, SecretKey key) {
    public DataEncryptionKey {
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (key == null || !"AES".equalsIgnoreCase(key.getAlgorithm()) || key.getEncoded() == null
                || key.getEncoded().length != 32) {
            throw new IllegalArgumentException("key must be a 256-bit AES key");
        }
    }
}
