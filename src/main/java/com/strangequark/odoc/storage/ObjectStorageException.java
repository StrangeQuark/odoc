package com.strangequark.odoc.storage;

/** Does not expose storage-provider diagnostics through application APIs. */
public class ObjectStorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
