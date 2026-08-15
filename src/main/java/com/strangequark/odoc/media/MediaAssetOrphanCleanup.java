package com.strangequark.odoc.media;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MediaAssetOrphanCleanup {
    private static final Duration MINIMUM_UPLOAD_GRACE_PERIOD = Duration.ofDays(7);
    /** Stable, application-specific PostgreSQL transaction advisory-lock key. */
    private static final long ORPHAN_CLEANUP_ADVISORY_LOCK = 0x4f444f435f4d4544L;
    private final MediaAssetService assets;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    MediaAssetOrphanCleanup(MediaAssetService assets, JdbcTemplate jdbcTemplate) {
        this(assets, jdbcTemplate, Clock.systemUTC());
    }

    MediaAssetOrphanCleanup(MediaAssetService assets, JdbcTemplate jdbcTemplate, Clock clock) {
        this.assets = assets;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${odoc.media.orphan-cleanup-interval:PT24H}")
    @Transactional
    public void deleteAbandonedUploads() {
        if (!tryAcquireReplicaSafeLock()) return;
        assets.deleteUnreferencedOlderThan(clock.instant().minus(MINIMUM_UPLOAD_GRACE_PERIOD));
    }

    private boolean tryAcquireReplicaSafeLock() {
        Boolean acquired = jdbcTemplate.queryForObject(
                "select pg_try_advisory_xact_lock(?)", Boolean.class, ORPHAN_CLEANUP_ADVISORY_LOCK);
        return Boolean.TRUE.equals(acquired);
    }
}
