package com.strangequark.odoc.jobs;

import com.strangequark.odoc.auth.OdocAuthProperties;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes encrypted, schema-versioned outbox records in the source domain transaction. */
@Service
public class OutboxPublisher {
    private final OutboxEventRepository events;
    private final ManagedRecordEncryption encryption;
    private final OdocAuthProperties authProperties;

    OutboxPublisher(OutboxEventRepository events, ManagedRecordEncryption encryption,
            OdocAuthProperties authProperties) {
        this.events = events;
        this.encryption = encryption;
        this.authProperties = authProperties;
    }

    @Transactional
    public UUID publish(UUID workspaceId, String aggregateType, UUID aggregateId, String eventType,
            Map<String, ?> payload, String idempotencyKey) {
        return events.findByIdempotencyKey(idempotencyKey).map(OutboxEvent::id).orElseGet(() -> {
            UUID id = UUID.randomUUID();
            String envelope = EncryptedPayloadCodec.encode(encryption.encrypt(
                    new EncryptionContext(scope(workspaceId), id, EncryptionPurpose.JOB_PAYLOAD, 1),
                    json(payload)));
            events.save(new OutboxEvent(id, workspaceId, aggregateType, aggregateId, eventType, 1,
                    envelope, idempotencyKey, Instant.now()));
            return id;
        });
    }

    /** Decodes only inside a trusted, authorized outbox consumer. */
    public Map<String, Object> payload(OutboxEvent event) {
        try {
            byte[] plaintext = encryption.decrypt(
                    new EncryptionContext(scope(event.workspaceId()), event.id(), EncryptionPurpose.JOB_PAYLOAD, event.schemaVersion()),
                    EncryptedPayloadCodec.decode(event.payloadEnvelope()));
            return PayloadRecordCodec.decode(plaintext);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decode outbox event payload.", exception);
        }
    }

    private byte[] json(Map<String, ?> payload) {
        return PayloadRecordCodec.encode(payload);
    }

    private SecurityScope scope(UUID workspaceId) {
        return workspaceId == null
                ? new SecurityScope(SecurityScopeKind.INSTANCE, authProperties.instanceScopeId())
                : new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceId);
    }
}
