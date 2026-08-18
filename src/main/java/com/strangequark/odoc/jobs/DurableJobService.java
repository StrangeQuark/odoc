package com.strangequark.odoc.jobs;

import com.strangequark.odoc.auth.OdocAuthProperties;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lease-fenced job state machine backed solely by PostgreSQL. */
@Service
public class DurableJobService {
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(30);
    private final DurableJobRepository jobs;
    private final ManagedRecordEncryption encryption;
    private final OdocAuthProperties authProperties;

    DurableJobService(DurableJobRepository jobs, ManagedRecordEncryption encryption,
            OdocAuthProperties authProperties) {
        this.jobs = jobs;
        this.encryption = encryption;
        this.authProperties = authProperties;
    }

    @Transactional
    public UUID enqueue(UUID workspaceId, String jobType, Map<String, ?> payload, String idempotencyKey) {
        return jobs.findByIdempotencyKey(idempotencyKey).map(DurableJob::id).orElseGet(() -> {
            UUID id = UUID.randomUUID();
            String envelope = EncryptedPayloadCodec.encode(encryption.encrypt(
                    new EncryptionContext(scope(workspaceId), id, EncryptionPurpose.JOB_PAYLOAD, 1), json(payload)));
            jobs.save(new DurableJob(id, workspaceId, jobType, 1, envelope, 0, null, idempotencyKey, 8,
                    Instant.now(), Instant.now()));
            return id;
        });
    }

    @Transactional
    public List<ClaimedJob> claim(String workerId, int limit) {
        Instant now = Instant.now();
        return jobs.lockClaimableIds(Math.max(1, Math.min(limit, 50))).stream()
                .map(id -> jobs.findById(id).orElseThrow())
                .filter(job -> job.claim(workerId, now, DEFAULT_LEASE))
                .map(job -> new ClaimedJob(job.id(), job.workspaceId(), job.jobType(), job.schemaVersion(),
                        decode(job), workerId, job.leaseEpoch(), job.idempotencyKey(), job.attemptCount()))
                .toList();
    }

    @Transactional
    public boolean heartbeat(ClaimedJob job) {
        return jobs.findById(job.id()).map(entity -> entity.heartbeat(
                job.workerId(), job.leaseEpoch(), Instant.now(), DEFAULT_LEASE)).orElse(false);
    }

    @Transactional
    public boolean complete(ClaimedJob job) {
        return jobs.findById(job.id()).map(entity -> entity.complete(
                job.workerId(), job.leaseEpoch(), Instant.now())).orElse(false);
    }

    @Transactional
    public boolean fail(ClaimedJob job, Exception exception) {
        Duration retryDelay = retryDelay(job.attemptCount());
        return jobs.findById(job.id()).map(entity -> entity.fail(job.workerId(), job.leaseEpoch(),
                Instant.now(), exception.getClass().getSimpleName(), retryDelay)).orElse(false);
    }

    @Transactional
    public boolean cancel(UUID jobId) {
        return jobs.findById(jobId).map(job -> job.cancelQueued(Instant.now())).orElse(false);
    }

    @Transactional
    public boolean cancel(ClaimedJob job) {
        return jobs.findById(job.id()).map(entity -> entity.cancel(
                job.workerId(), job.leaseEpoch(), Instant.now())).orElse(false);
    }

    private Map<String, Object> decode(DurableJob job) {
        try {
            byte[] plaintext = encryption.decrypt(new EncryptionContext(scope(job.workspaceId()), job.id(),
                    EncryptionPurpose.JOB_PAYLOAD, job.schemaVersion()), EncryptedPayloadCodec.decode(job.payloadEnvelope()));
            return PayloadRecordCodec.decode(plaintext);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decode durable job payload.", exception);
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

    /** Capped exponential retry with bounded jitter so a failed batch does not retry in lockstep. */
    private static Duration retryDelay(int attempts) {
        long baseSeconds = Math.min(300, 1L << Math.min(8, Math.max(0, attempts)));
        long jitterMillis = ThreadLocalRandom.current().nextLong(Math.max(1, baseSeconds * 200L + 1));
        return Duration.ofSeconds(baseSeconds).plusMillis(jitterMillis);
    }

    public record ClaimedJob(UUID id, UUID workspaceId, String type, int schemaVersion, Map<String, Object> payload,
            String workerId, long leaseEpoch, String idempotencyKey, int attemptCount) {}
}
