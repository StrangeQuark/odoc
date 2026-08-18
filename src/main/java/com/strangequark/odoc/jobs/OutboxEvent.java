package com.strangequark.odoc.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Transactional domain event that is delivered only after its source transaction commits. */
@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @Column(name = "payload_envelope", nullable = false) private String payloadEnvelope;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OutboxEventState state;
    @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "run_after", nullable = false) private Instant runAfter;
    @Column(name = "lease_owner") private String leaseOwner;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "lease_epoch", nullable = false) private long leaseEpoch;
    @Column(name = "last_error") private String lastError;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;

    protected OutboxEvent() {}

    OutboxEvent(UUID id, UUID workspaceId, String aggregateType, UUID aggregateId, String eventType,
            int schemaVersion, String payloadEnvelope, String idempotencyKey, Instant occurredAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payloadEnvelope = payloadEnvelope;
        this.state = OutboxEventState.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.runAfter = occurredAt;
        this.occurredAt = occurredAt;
    }

    UUID id() { return id; }
    UUID workspaceId() { return workspaceId; }
    String aggregateType() { return aggregateType; }
    UUID aggregateId() { return aggregateId; }
    String eventType() { return eventType; }
    int schemaVersion() { return schemaVersion; }
    String payloadEnvelope() { return payloadEnvelope; }
    long leaseEpoch() { return leaseEpoch; }

    boolean claim(String owner, Instant now, Duration lease) {
        if (!claimableAt(now)) return false;
        state = OutboxEventState.PROCESSING;
        leaseOwner = owner;
        leaseExpiresAt = now.plus(lease);
        leaseEpoch++;
        attemptCount++;
        return true;
    }

    boolean heartbeat(String owner, long epoch, Instant now, Duration lease) {
        if (!fenced(owner, epoch) || leaseExpiresAt == null || !leaseExpiresAt.isAfter(now)) return false;
        leaseExpiresAt = now.plus(lease);
        return true;
    }

    boolean publish(String owner, long epoch, Instant now) {
        if (!fenced(owner, epoch)) return false;
        state = OutboxEventState.PUBLISHED;
        publishedAt = now;
        clearLease();
        return true;
    }

    boolean retry(String owner, long epoch, Instant now, String diagnostic, Duration delay) {
        if (!fenced(owner, epoch)) return false;
        state = attemptCount >= 8 ? OutboxEventState.DEAD_LETTER : OutboxEventState.PENDING;
        runAfter = now.plus(delay);
        lastError = safeDiagnostic(diagnostic);
        clearLease();
        return true;
    }

    private boolean claimableAt(Instant now) {
        return (state == OutboxEventState.PENDING && !runAfter.isAfter(now))
                || (state == OutboxEventState.PROCESSING && leaseExpiresAt != null && !leaseExpiresAt.isAfter(now));
    }

    private boolean fenced(String owner, long epoch) {
        return state == OutboxEventState.PROCESSING && leaseEpoch == epoch && owner.equals(leaseOwner);
    }

    private void clearLease() { leaseOwner = null; leaseExpiresAt = null; }

    private static String safeDiagnostic(String value) {
        if (value == null) return "consumer failed";
        return value.replaceAll("[\\r\\n]", " ").substring(0, Math.min(512, value.length()));
    }
}
