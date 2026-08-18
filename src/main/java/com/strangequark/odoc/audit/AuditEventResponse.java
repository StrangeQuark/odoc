package com.strangequark.odoc.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, UUID actorUserId, String action, String targetType, UUID targetId,
        String outcome, String requestId, Instant occurredAt) {
    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.id(), event.actorUserId(), event.action(), event.targetType(), event.targetId(),
                event.outcome(), event.requestId(), event.occurredAt());
    }
}
