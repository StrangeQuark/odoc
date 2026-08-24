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
     * Deliberately projects only routing metadata. Loading {@link MediaAsset}
     * here would materialize a legacy bytea payload for every abandoned upload.
     */
    @Query("select asset.id as id, asset.objectKey as objectKey from MediaAsset asset "
            + "where asset.createdAt < :cutoff order by asset.createdAt asc")
    List<MediaAssetStorageReference> findStorageReferencesByCreatedAtBefore(
            @Param("cutoff") Instant cutoff, Pageable pageable);

    /** Deletes without selecting the media blob into the application first. */
    @Modifying
    @Query("delete from MediaAsset asset where asset.id = :id")
    int deleteDirectlyById(@Param("id") UUID id);

    @Modifying
    @Query(value = "update media_assets set storage_state = 'DELETE_PENDING' where id = :id", nativeQuery = true)
    int markDeletionPending(@Param("id") UUID id);

    interface MediaAssetStorageReference {
        UUID getId();
        String getObjectKey();
    }
}
