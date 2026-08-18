package com.strangequark.odoc.encryption;

/** Intentionally non-diagnostic failure surface for managed encryption callers. */
public final class ManagedEncryptionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ManagedEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
