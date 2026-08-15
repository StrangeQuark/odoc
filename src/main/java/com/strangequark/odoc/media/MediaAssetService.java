package com.strangequark.odoc.media;

import com.strangequark.odoc.space.SpaceRepository;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class MediaAssetService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private final MediaAssetRepository assets;
    private final SpaceRepository spaces;
    private final Clock clock;

    @Autowired
    MediaAssetService(MediaAssetRepository assets, SpaceRepository spaces) {
        this(assets, spaces, Clock.systemUTC());
    }

    MediaAssetService(MediaAssetRepository assets, SpaceRepository spaces, Clock clock) {
        this.assets = assets;
        this.spaces = spaces;
        this.clock = clock;
    }

    @Transactional
    MediaAssetResponse upload(UUID spaceId, MultipartFile file) {
        if (!spaces.existsById(spaceId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found.");
        String contentType = file.getContentType();
        if (!SUPPORTED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a PNG, JPEG, GIF, or WebP image.");
        }
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty image.");
        try {
            MediaAsset asset = new MediaAsset(UUID.randomUUID(), spaceId, safeFilename(file.getOriginalFilename()),
                    contentType, file.getBytes(), clock.instant());
            return MediaAssetResponse.from(assets.save(asset));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded image.", exception);
        }
    }

    @Transactional(readOnly = true)
    MediaAsset get(UUID id) {
        return assets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found."));
    }

    @Transactional
    void delete(UUID id) { assets.delete(get(id)); }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "image";
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
