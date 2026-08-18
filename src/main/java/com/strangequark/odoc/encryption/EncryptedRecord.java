package com.strangequark.odoc.encryption;

import java.util.Arrays;
import java.util.Objects;

/** Versioned bounded-record envelope. The context remains external and is authenticated as AAD. */
public record EncryptedRecord(int formatVersion, String algorithm, int keyVersion, byte[] nonce, byte[] ciphertext) {
    public static final int FORMAT_VERSION = 1;
    public static final String ALGORITHM = "AES-256-GCM";

    public EncryptedRecord {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported encryption record format");
        }
        if (!ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("unsupported encryption record algorithm");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(ciphertext, "ciphertext");
        if (nonce.length != 12) {
            throw new IllegalArgumentException("AES-GCM nonce must be 96 bits");
        }
        if (ciphertext.length < 16) {
            throw new IllegalArgumentException("ciphertext must contain the GCM tag");
        }
        nonce = Arrays.copyOf(nonce, nonce.length);
        ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }
}
