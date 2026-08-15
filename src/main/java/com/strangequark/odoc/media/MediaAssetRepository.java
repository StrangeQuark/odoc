package com.strangequark.odoc.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    /**
     * Deliberately projects only IDs. Loading {@link MediaAsset} here would
     * materialize its bytea content for every abandoned upload in the sweep.
     */
    @Query("select asset.id from MediaAsset asset where asset.createdAt < :cutoff order by asset.createdAt asc")
    List<UUID> findIdsByCreatedAtBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    /** Deletes without selecting the media blob into the application first. */
    @Modifying
    @Query("delete from MediaAsset asset where asset.id = :id")
    int deleteDirectlyById(@Param("id") UUID id);
}
