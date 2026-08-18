package com.strangequark.odoc.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Claims, dispatches, and fences outbox delivery. It may run concurrently in every API/worker pod. */
@Component
public class OutboxDispatcher {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final OutboxEventRepository events;
    private final OutboxPublisher publisher;
    private final List<OutboxHandler> handlers;
    private final String workerId = "odoc-outbox-" + UUID.randomUUID();

    OutboxDispatcher(OutboxEventRepository events, OutboxPublisher publisher, List<OutboxHandler> handlers) {
        this.events = events;
        this.publisher = publisher;
        this.handlers = handlers;
    }

    @Scheduled(fixedDelayString = "${odoc.outbox.poll-interval:PT1S}")
    void poll() { dispatch(16); }

    @Transactional
    public int dispatch(int limit) {
        int delivered = 0;
        Instant now = Instant.now();
        for (UUID id : events.lockClaimableIds(Math.max(1, Math.min(limit, 100)))) {
            OutboxEvent entity = events.findById(id).orElse(null);
            if (entity == null || !entity.claim(workerId, now, LEASE)) continue;
            ClaimedOutboxEvent claimed = new ClaimedOutboxEvent(entity.id(), entity.workspaceId(), entity.aggregateType(),
                    entity.aggregateId(), entity.eventType(), entity.schemaVersion(), publisher.payload(entity),
                    workerId, entity.leaseEpoch());
            try {
                OutboxHandler handler = handlers.stream().filter(candidate -> candidate.supports(claimed.type()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("No outbox handler for event type."));
                handler.handle(claimed);
                if (entity.publish(workerId, claimed.leaseEpoch(), Instant.now())) delivered++;
            } catch (Exception exception) {
                entity.retry(workerId, claimed.leaseEpoch(), Instant.now(), exception.getClass().getSimpleName(), retryDelay(entity));
            }
        }
        return delivered;
    }

    @Transactional
    public boolean heartbeat(ClaimedOutboxEvent event) {
        return events.findById(event.id()).map(entity -> entity.heartbeat(
                event.workerId(), event.leaseEpoch(), Instant.now(), LEASE)).orElse(false);
    }

    private static Duration retryDelay(OutboxEvent event) {
        long baseSeconds = Math.min(300, 1L << Math.min(8, Math.max(0, event.leaseEpoch())));
        return Duration.ofSeconds(baseSeconds).plusMillis(
                ThreadLocalRandom.current().nextLong(Math.max(1, baseSeconds * 200L + 1)));
    }

    public record ClaimedOutboxEvent(UUID id, UUID workspaceId, String aggregateType, UUID aggregateId, String type,
            int schemaVersion, Map<String, Object> payload, String workerId, long leaseEpoch) {}
}
