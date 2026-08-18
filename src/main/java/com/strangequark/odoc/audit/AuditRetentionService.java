package com.strangequark.odoc.audit;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit retention hook. Scheduling, legal holds, and export policy are deployment choices;
 * application code has no general-purpose audit delete path.
 */
@Service
class AuditRetentionService {
    private final AuditEventRepository events;

    AuditRetentionService(AuditEventRepository events) { this.events = events; }

    @Transactional
    int purgeBefore(Instant cutoff) {
        if (cutoff == null) throw new IllegalArgumentException("Audit retention cutoff is required.");
        return events.deleteBefore(cutoff);
    }
}
