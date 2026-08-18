package com.strangequark.odoc.encryption;

/**
 * Purpose separation prevents ciphertext for one class of data from being replayed as another.
 * Additional purposes are added with their owning persistence package.
 */
public enum EncryptionPurpose {
    IDENTITY,
    IDENTITY_LOOKUP,
    SESSION,
    AUTH_RATE_LIMIT,
    AUTH_AUDIT,
    AUDIT,
    WORKSPACE_METADATA,
    WORKSPACE_LOOKUP,
    WORKSPACE_CONTENT,
    JOB_PAYLOAD,
    INTEGRATION_SECRET,
    MEDIA
}
