package com.strangequark.odoc.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only application audit record. No public setter or repository deletion path exists. */
@Entity
@Table(name = "audit_events")
class AuditEvent {
    @Id private UUID id;
    @Column(name = "workspace_id") private UUID workspaceId;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(nullable = false) private String action;
    @Column(name = "target_type", nullable = false) private String targetType;
    @Column(name = "target_id") private UUID targetId;
    @Column(nullable = false) private String outcome;
    @Column(name = "request_id") private String requestId;
    @Column(name = "metadata_envelope", nullable = false) private String metadataEnvelope;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "source_outbox_id", nullable = false, unique = true) private UUID sourceOutboxId;

    protected AuditEvent() {}
    AuditEvent(UUID id, UUID workspaceId, UUID actorUserId, String action, String targetType, UUID targetId,
            String outcome, String requestId, String metadataEnvelope, Instant occurredAt, UUID sourceOutboxId) {
        this.id = id; this.workspaceId = workspaceId; this.actorUserId = actorUserId; this.action = action;
        this.targetType = targetType; this.targetId = targetId; this.outcome = outcome; this.requestId = requestId;
        this.metadataEnvelope = metadataEnvelope; this.occurredAt = occurredAt; this.sourceOutboxId = sourceOutboxId;
    }
    UUID id() { return id; }
    UUID workspaceId() { return workspaceId; }
    UUID actorUserId() { return actorUserId; }
    String action() { return action; }
    String targetType() { return targetType; }
    UUID targetId() { return targetId; }
    String outcome() { return outcome; }
    String requestId() { return requestId; }
    Instant occurredAt() { return occurredAt; }
}
