package com.strangequark.odoc.jobs;

public enum DurableJobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    DEAD_LETTER,
    CANCELLED
}
