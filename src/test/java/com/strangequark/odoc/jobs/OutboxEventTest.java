package com.strangequark.odoc.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
    private static final Instant NOW = Instant.parse("2026-08-18T15:00:00Z");

    @Test
    void aRecoveredLeaseCannotBePublishedByItsOldOwner() {
        OutboxEvent event = event();
        assertThat(event.claim("worker-a", NOW, Duration.ofSeconds(30))).isTrue();
        long firstEpoch = event.leaseEpoch();
        assertThat(event.claim("worker-b", NOW.plusSeconds(31), Duration.ofSeconds(30))).isTrue();
        long recoveredEpoch = event.leaseEpoch();

        assertThat(event.publish("worker-a", firstEpoch, NOW.plusSeconds(31))).isFalse();
        assertThat(event.publish("worker-b", recoveredEpoch, NOW.plusSeconds(31))).isTrue();
        assertThat(event.claim("worker-c", NOW.plusSeconds(60), Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void failedDeliveryEventuallyGoesToDeadLetter() {
        OutboxEvent event = event();
        for (int attempt = 0; attempt < 8; attempt++) {
            Instant attemptTime = NOW.plusSeconds(attempt * 400L);
            assertThat(event.claim("worker", attemptTime, Duration.ofSeconds(30))).isTrue();
            assertThat(event.retry("worker", event.leaseEpoch(), attemptTime, "handler failed", Duration.ofSeconds(1))).isTrue();
        }
        assertThat(event.claim("worker", NOW.plusSeconds(10_000), Duration.ofSeconds(30))).isFalse();
    }

    private static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "workspace", UUID.randomUUID(), "audit.v1", 1,
                "ciphertext", "dedupe-" + UUID.randomUUID(), NOW);
    }
}
