package com.strangequark.odoc.jobs;

/** Handlers must make external effects idempotent with {@link DurableJobService.ClaimedJob#idempotencyKey()}. */
public interface DurableJobHandler {
    boolean supports(String jobType);
    void handle(DurableJobService.ClaimedJob job) throws Exception;
}
