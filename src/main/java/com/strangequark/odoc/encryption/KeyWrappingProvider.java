package com.strangequark.odoc.encryption;

import javax.crypto.SecretKey;

/** Port for an operator-managed KEK/KMS/HSM that wraps scoped data-encryption keys. */
public interface KeyWrappingProvider {
    int wrappingKeyVersion();

    byte[] wrap(SecretKey dataEncryptionKey);

    SecretKey unwrap(int wrappingKeyVersion, byte[] wrappedDataEncryptionKey);
}
