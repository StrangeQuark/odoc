package com.strangequark.odoc.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DurableJobRepository extends JpaRepository<DurableJob, UUID> {
    Optional<DurableJob> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            SELECT id FROM durable_jobs
            WHERE (state = 'QUEUED' AND run_after <= CURRENT_TIMESTAMP)
               OR (state = 'RUNNING' AND lease_expires_at <= CURRENT_TIMESTAMP)
            ORDER BY priority DESC, run_after, created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> lockClaimableIds(@Param("limit") int limit);
}
