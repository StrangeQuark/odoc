package com.strangequark.odoc.media;

/** Metadata state; object bytes are never made available until verified. */
enum MediaStorageState {
    AVAILABLE,
    DELETE_PENDING
}
