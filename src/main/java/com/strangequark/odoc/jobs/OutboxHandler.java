package com.strangequark.odoc.jobs;

/** A deterministic outbox consumer. The source event ID is its idempotency/fencing identity. */
public interface OutboxHandler {
    boolean supports(String eventType);
    void handle(OutboxDispatcher.ClaimedOutboxEvent event) throws Exception;
}
