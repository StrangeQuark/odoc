package com.strangequark.odoc.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableJobTest {
    private static final Instant NOW = Instant.parse("2026-08-18T15:00:00Z");

    @Test
    void fencesAPausedWorkerAfterAnotherWorkerRecoversTheLease() {
        DurableJob job = job();

        assertThat(job.claim("worker-a", NOW, Duration.ofSeconds(30))).isTrue();
        long firstEpoch = job.leaseEpoch();
        assertThat(job.claim("worker-b", NOW.plusSeconds(31), Duration.ofSeconds(30))).isTrue();
        long recoveredEpoch = job.leaseEpoch();

        assertThat(recoveredEpoch).isGreaterThan(firstEpoch);
        assertThat(job.heartbeat("worker-a", firstEpoch, NOW.plusSeconds(31), Duration.ofSeconds(30))).isFalse();
        assertThat(job.complete("worker-a", firstEpoch, NOW.plusSeconds(31))).isFalse();
        assertThat(job.cancel("worker-a", firstEpoch, NOW.plusSeconds(31))).isFalse();
        assertThat(job.complete("worker-b", recoveredEpoch, NOW.plusSeconds(31))).isTrue();
        assertThat(job.state()).isEqualTo(DurableJobState.SUCCEEDED);
    }

    @Test
    void retriesWithBackoffThenRetainsPoisonWorkInDeadLetter() {
        DurableJob job = job();
        for (int attempt = 0; attempt < 8; attempt++) {
            Instant attemptTime = NOW.plusSeconds(attempt * 400L);
            assertThat(job.claim("worker", attemptTime, Duration.ofSeconds(30))).isTrue();
            assertThat(job.fail("worker", job.leaseEpoch(), attemptTime, "simulated failure", Duration.ofSeconds(1))).isTrue();
        }
        assertThat(job.state()).isEqualTo(DurableJobState.DEAD_LETTER);
        assertThat(job.claim("another-worker", NOW.plusSeconds(10_000), Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void cancellationPreventsFurtherClaims() {
        DurableJob job = job();
        assertThat(job.cancelQueued(NOW)).isTrue();
        assertThat(job.claim("worker", NOW, Duration.ofSeconds(30))).isFalse();
        assertThat(job.cancelQueued(NOW.plusSeconds(1))).isFalse();
    }

    private static DurableJob job() {
        return new DurableJob(UUID.randomUUID(), UUID.randomUUID(), "sample.v1", 1, "ciphertext", 0, null,
                "dedupe-" + UUID.randomUUID(), 8, NOW, NOW);
    }
}
