package com.strangequark.odoc.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Optional<AuditEvent> findBySourceOutboxId(UUID sourceOutboxId);
    @Query("""
            SELECT event FROM AuditEvent event WHERE event.workspaceId = :workspaceId
              AND (:before IS NULL OR event.occurredAt < :before
                   OR (event.occurredAt = :before AND event.id < :beforeId))
            ORDER BY event.occurredAt DESC, event.id DESC
            """)
    List<AuditEvent> findPage(UUID workspaceId, Instant before, UUID beforeId, Pageable pageable);

    /** Deliberately not exposed through a controller; retention is an operator-owned service. */
    @Modifying
    @Query("DELETE FROM AuditEvent event WHERE event.occurredAt < :before")
    int deleteBefore(Instant before);
}
