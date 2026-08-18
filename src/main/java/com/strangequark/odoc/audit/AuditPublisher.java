package com.strangequark.odoc.audit;

import com.strangequark.odoc.jobs.OutboxPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Writes safe audit intent to the transactional outbox; content and credentials are never accepted here. */
@Service
public class AuditPublisher {
    private final OutboxPublisher outbox;
    private final ObjectProvider<HttpServletRequest> request;

    AuditPublisher(OutboxPublisher outbox, ObjectProvider<HttpServletRequest> request) {
        this.outbox = outbox;
        this.request = request;
    }

    public UUID record(UUID workspaceId, UUID actorUserId, String action, String targetType, UUID targetId,
            String outcome, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (actorUserId != null) payload.put("actorUserId", actorUserId.toString());
        payload.put("action", action);
        payload.put("targetType", targetType);
        if (targetId != null) payload.put("targetId", targetId.toString());
        payload.put("outcome", outcome);
        HttpServletRequest servletRequest = request.getIfAvailable();
        Object requestId = servletRequest == null ? null : servletRequest.getAttribute("X-Request-Id");
        if (requestId instanceof String value) payload.put("requestId", value);
        payload.put("occurredAt", Instant.now().toString());
        return outbox.publish(workspaceId, targetType, targetId == null ? workspaceId : targetId,
                "audit.v1", payload, idempotencyKey);
    }
}
