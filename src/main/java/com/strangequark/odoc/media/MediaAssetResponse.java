package com.strangequark.odoc.media;

import java.time.Instant;
import java.util.UUID;

record MediaAssetResponse(UUID id, UUID spaceId, String filename, String contentType, long sizeBytes, Instant createdAt, String url) {
    static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(asset.id(), asset.spaceId(), asset.filename(), asset.contentType(),
                asset.sizeBytes(), asset.createdAt(), "/api/v1/media/" + asset.id());
    }
    static MediaAssetResponse from(MediaAsset asset, String filename) {
        return new MediaAssetResponse(asset.id(), asset.spaceId(), filename, asset.contentType(),
                asset.sizeBytes(), asset.createdAt(), "/api/v1/media/" + asset.id());
    }
}
