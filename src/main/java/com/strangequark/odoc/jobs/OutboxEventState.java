package com.strangequark.odoc.jobs;

public enum OutboxEventState {
    PENDING,
    PROCESSING,
    PUBLISHED,
    DEAD_LETTER
}
