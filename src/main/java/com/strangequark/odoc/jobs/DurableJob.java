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

/** A database-owned job record. Every lease-changing method is fenced by its epoch. */
@Entity
@Table(name = "durable_jobs")
class DurableJob {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "job_type", nullable = false) private String jobType;
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @Column(name = "payload_envelope", nullable = false) private String payloadEnvelope;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DurableJobState state;
    @Column(nullable = false) private int priority;
    @Column(name = "concurrency_key") private String concurrencyKey;
    @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "run_after", nullable = false) private Instant runAfter;
    @Column(name = "lease_owner") private String leaseOwner;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "lease_epoch", nullable = false) private long leaseEpoch;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;

    protected DurableJob() {}

    DurableJob(UUID id, UUID workspaceId, String jobType, int schemaVersion, String payloadEnvelope,
            int priority, String concurrencyKey, String idempotencyKey, int maxAttempts, Instant runAfter, Instant now) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.jobType = jobType;
        this.schemaVersion = schemaVersion;
        this.payloadEnvelope = payloadEnvelope;
        this.state = DurableJobState.QUEUED;
        this.priority = priority;
        this.concurrencyKey = concurrencyKey;
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts;
        this.runAfter = runAfter;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID workspaceId() { return workspaceId; }
    String jobType() { return jobType; }
    int schemaVersion() { return schemaVersion; }
    String payloadEnvelope() { return payloadEnvelope; }
    String idempotencyKey() { return idempotencyKey; }
    long leaseEpoch() { return leaseEpoch; }
    int attemptCount() { return attemptCount; }
    DurableJobState state() { return state; }

    boolean claim(String owner, Instant now, Duration lease) {
        if (!claimableAt(now)) return false;
        state = DurableJobState.RUNNING;
        leaseOwner = owner;
        leaseExpiresAt = now.plus(lease);
        leaseEpoch++;
        attemptCount++;
        updatedAt = now;
        return true;
    }

    boolean heartbeat(String owner, long epoch, Instant now, Duration lease) {
        if (!fenced(owner, epoch) || leaseExpiresAt == null || !leaseExpiresAt.isAfter(now)) return false;
        leaseExpiresAt = now.plus(lease);
        updatedAt = now;
        return true;
    }

    boolean complete(String owner, long epoch, Instant now) {
        if (!fenced(owner, epoch)) return false;
        state = DurableJobState.SUCCEEDED;
        completedAt = now;
        clearLease();
        updatedAt = now;
        return true;
    }

    boolean fail(String owner, long epoch, Instant now, String diagnostic, Duration delay) {
        if (!fenced(owner, epoch)) return false;
        lastError = safeDiagnostic(diagnostic);
        clearLease();
        updatedAt = now;
        if (attemptCount >= maxAttempts) {
            state = DurableJobState.DEAD_LETTER;
        } else {
            state = DurableJobState.QUEUED;
            runAfter = now.plus(delay);
        }
        return true;
    }

    /** Operator cancellation is safe only before a worker owns a lease. */
    boolean cancelQueued(Instant now) {
        if (state != DurableJobState.QUEUED) return false;
        state = DurableJobState.CANCELLED;
        cancelledAt = now;
        clearLease();
        updatedAt = now;
        return true;
    }

    /** A running worker may stop only if it still owns the exact lease epoch. */
    boolean cancel(String owner, long epoch, Instant now) {
        if (!fenced(owner, epoch)) return false;
        state = DurableJobState.CANCELLED;
        cancelledAt = now;
        clearLease();
        updatedAt = now;
        return true;
    }

    private boolean claimableAt(Instant now) {
        return (state == DurableJobState.QUEUED && !runAfter.isAfter(now))
                || (state == DurableJobState.RUNNING && leaseExpiresAt != null && !leaseExpiresAt.isAfter(now));
    }

    private boolean fenced(String owner, long epoch) {
        return state == DurableJobState.RUNNING && epoch == leaseEpoch && owner.equals(leaseOwner);
    }

    private void clearLease() { leaseOwner = null; leaseExpiresAt = null; }

    private static String safeDiagnostic(String value) {
        if (value == null) return "handler failed";
        return value.replaceAll("[\\r\\n]", " ").substring(0, Math.min(512, value.length()));
    }
}
