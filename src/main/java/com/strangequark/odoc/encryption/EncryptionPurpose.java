package com.strangequark.odoc.encryption;

/**
 * Purpose separation prevents ciphertext for one class of data from being replayed as another.
 * Additional purposes are added with their owning persistence package.
 */
public enum EncryptionPurpose {
    IDENTITY,
    IDENTITY_LOOKUP,
    SESSION,
    WORKSPACE_CONTENT,
    JOB_PAYLOAD,
    INTEGRATION_SECRET,
    MEDIA
}
