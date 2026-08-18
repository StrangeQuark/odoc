package com.strangequark.odoc.storage;

import java.time.Instant;
import java.util.List;

/**
 * Server-side-only object-storage boundary. The browser never receives this
 * client's credentials or a managed-profile presigned URL.
 */
public interface ObjectStorage {
    void put(String key, byte[] ciphertext, String contentType);

    byte[] get(String key);

    void delete(String key);

    /** Lists only a caller-supplied, application-owned prefix for bounded reconciliation. */
    List<StoredObject> list(String prefix, int limit);

    record StoredObject(String key, Instant lastModified) {}
}
