package com.strangequark.odoc.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MediaAssetOrphanCleanupTest {
    @Mock private MediaAssetService assets;
    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void runsTheSweepOnlyWhenThisReplicaAcquiresTheAdvisoryLock() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(true);

        cleanupAt(now).deleteAbandonedUploads();

        verify(assets).deleteUnreferencedOlderThan(Instant.parse("2026-08-07T00:00:00Z"));
    }

    @Test
    void skipsTheSweepWhenAnotherReplicaOwnsTheAdvisoryLock() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);

        cleanupAt(Instant.parse("2026-08-14T00:00:00Z")).deleteAbandonedUploads();

        verify(assets, never()).deleteUnreferencedOlderThan(any());
    }

    private MediaAssetOrphanCleanup cleanupAt(Instant now) {
        return new MediaAssetOrphanCleanup(assets, jdbcTemplate, Clock.fixed(now, ZoneOffset.UTC));
    }
}
