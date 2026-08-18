package com.strangequark.odoc.encryption;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * A development adapter for the key-wrapping port. Production deployments must use a reviewed
 * external KMS/HSM adapter; this class deliberately refuses a missing or non-256-bit operator key.
 */
final class LocalAesKeyWrappingProvider implements KeyWrappingProvider {
    private static final int VERSION = 1;
    private final SecretKey wrappingKey;

    LocalAesKeyWrappingProvider(String wrappingKeyBase64) {
        byte[] key = Base64.getDecoder().decode(wrappingKeyBase64);
        if (key.length != 32) {
            throw new IllegalArgumentException("Managed encryption wrapping key must be 256 bits.");
        }
        this.wrappingKey = new SecretKeySpec(Arrays.copyOf(key, key.length), "AES");
    }

    @Override
    public int wrappingKeyVersion() {
        return VERSION;
    }

    @Override
    public byte[] wrap(SecretKey dataEncryptionKey) {
        try {
            Cipher cipher = Cipher.getInstance("AESWrap");
            cipher.init(Cipher.WRAP_MODE, wrappingKey);
            return cipher.wrap(dataEncryptionKey);
        } catch (GeneralSecurityException exception) {
            throw new ManagedEncryptionException("Unable to wrap the data key.", exception);
        }
    }

    @Override
    public SecretKey unwrap(int wrappingKeyVersion, byte[] wrappedDataEncryptionKey) {
        if (wrappingKeyVersion != VERSION) {
            throw new ManagedEncryptionException("Unable to unwrap the data key.", null);
        }
        try {
            Cipher cipher = Cipher.getInstance("AESWrap");
            cipher.init(Cipher.UNWRAP_MODE, wrappingKey);
            return (SecretKey) cipher.unwrap(wrappedDataEncryptionKey, "AES", Cipher.SECRET_KEY);
        } catch (GeneralSecurityException exception) {
            throw new ManagedEncryptionException("Unable to unwrap the data key.", exception);
        }
    }
}
