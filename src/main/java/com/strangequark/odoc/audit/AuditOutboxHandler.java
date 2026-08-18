package com.strangequark.odoc.audit;

import com.strangequark.odoc.auth.OdocAuthProperties;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import com.strangequark.odoc.jobs.EncryptedPayloadCodec;
import com.strangequark.odoc.jobs.OutboxDispatcher;
import com.strangequark.odoc.jobs.OutboxHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Idempotently materializes application audit events from the durable outbox. */
@Component
class AuditOutboxHandler implements OutboxHandler {
    private final AuditEventRepository audits;
    private final ManagedRecordEncryption encryption;
    private final OdocAuthProperties authProperties;

    AuditOutboxHandler(AuditEventRepository audits, ManagedRecordEncryption encryption,
            OdocAuthProperties authProperties) {
        this.audits = audits; this.encryption = encryption; this.authProperties = authProperties;
    }
    @Override public boolean supports(String eventType) { return "audit.v1".equals(eventType); }
    @Override public void handle(OutboxDispatcher.ClaimedOutboxEvent event) {
        if (audits.findBySourceOutboxId(event.id()).isPresent()) return;
        Map<String, Object> payload = event.payload();
        UUID auditId = UUID.randomUUID();
        String metadata = EncryptedPayloadCodec.encode(encryption.encrypt(
                new EncryptionContext(scope(event.workspaceId()), auditId, EncryptionPurpose.AUDIT, 1), "schema:1".getBytes(StandardCharsets.UTF_8)));
        audits.save(new AuditEvent(auditId, event.workspaceId(), optionalUuid(payload, "actorUserId"), string(payload, "action"),
                string(payload, "targetType"), optionalUuid(payload, "targetId"), string(payload, "outcome"),
                optionalString(payload, "requestId"), metadata, Instant.parse(string(payload, "occurredAt")), event.id()));
    }
    private SecurityScope scope(UUID workspaceId) {
        return workspaceId == null ? new SecurityScope(SecurityScopeKind.INSTANCE, authProperties.instanceScopeId())
                : new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceId);
    }
    private static String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Invalid audit payload.");
        return text;
    }
    private static String optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key); return value instanceof String text ? text : null;
    }
    private static UUID uuid(Map<String, Object> payload, String key) { return UUID.fromString(string(payload, key)); }
    private static UUID optionalUuid(Map<String, Object> payload, String key) {
        String value = optionalString(payload, key); return value == null ? null : UUID.fromString(value);
    }
}
