package com.strangequark.odoc.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            SELECT id FROM outbox_events
            WHERE (state = 'PENDING' AND run_after <= CURRENT_TIMESTAMP)
               OR (state = 'PROCESSING' AND lease_expires_at <= CURRENT_TIMESTAMP)
            ORDER BY occurred_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> lockClaimableIds(@Param("limit") int limit);
}
