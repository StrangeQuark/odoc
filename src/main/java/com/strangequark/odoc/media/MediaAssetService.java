package com.strangequark.odoc.media;

import com.strangequark.odoc.page.PageRepository;
import com.strangequark.odoc.page.PageVersionRepository;
import com.strangequark.odoc.space.SpaceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class MediaAssetService {
    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;
    static final int ORPHAN_CLEANUP_BATCH_SIZE = 100;
    private static final int WEBM_HEADER_SCAN_LIMIT_BYTES = 4 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif");
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif", "video/mp4", "video/webm", "video/ogg");
    private final MediaAssetRepository assets;
    private final SpaceRepository spaces;
    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final Clock clock;

    @Autowired
    MediaAssetService(MediaAssetRepository assets, SpaceRepository spaces, PageRepository pages,
            PageVersionRepository versions) {
        this(assets, spaces, pages, versions, Clock.systemUTC());
    }

    MediaAssetService(MediaAssetRepository assets, SpaceRepository spaces, PageRepository pages,
            PageVersionRepository versions, Clock clock) {
        this.assets = assets;
        this.spaces = spaces;
        this.pages = pages;
        this.versions = versions;
        this.clock = clock;
    }

    @Transactional
    MediaAssetResponse upload(UUID spaceId, MultipartFile file) {
        if (!spaces.existsById(spaceId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found.");
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || !SUPPORTED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Use a PNG, JPEG, GIF, WebP, AVIF, MP4, WebM, or Ogg media file.");
        }
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty media file.");
        long limit = IMAGE_TYPES.contains(contentType) ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (file.getSize() > limit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    (IMAGE_TYPES.contains(contentType) ? "Images" : "Videos") + " must be " + limit / 1024 / 1024 + " MB or smaller.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasExpectedSignature(contentType, bytes)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "The uploaded file does not match its declared media type.");
            }
            MediaAsset asset = new MediaAsset(UUID.randomUUID(), spaceId, safeFilename(file.getOriginalFilename()),
                    contentType, bytes, clock.instant());
            return MediaAssetResponse.from(assets.save(asset));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded media.", exception);
        }
    }

    @Transactional(readOnly = true)
    MediaAsset get(UUID id) {
        return assets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found."));
    }

    @Transactional
    boolean deleteIfUnreferenced(UUID id) {
        if (!assets.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        }
        if (isReferenced(id)) return false;
        return assets.deleteDirectlyById(id) > 0;
    }

    /**
     * A page version deliberately retains its referenced media. This delayed
     * sweep only removes abandoned uploads after the user has had ample time
     * to publish a long-running edit.
     */
    @Transactional
    int deleteUnreferencedOlderThan(Instant cutoff) {
        int removed = 0;
        for (UUID id : assets.findIdsByCreatedAtBefore(cutoff,
                PageRequest.of(0, ORPHAN_CLEANUP_BATCH_SIZE))) {
            if (!isReferenced(id) && assets.deleteDirectlyById(id) > 0) {
                removed++;
            }
        }
        return removed;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "media";
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean isReferenced(UUID assetId) {
        String path = "/api/v1/media/" + assetId;
        return pages.existsByContentContaining(path) || versions.existsByContentContaining(path);
    }

    static boolean hasExpectedSignature(String contentType, byte[] bytes) {
        if (bytes.length < 4) return false;
        return switch (contentType) {
            case "image/png" -> startsWith(bytes, (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
            case "image/jpeg" -> startsWith(bytes, (byte) 0xff, (byte) 0xd8, (byte) 0xff);
            case "image/gif" -> startsWithAscii(bytes, "GIF87a") || startsWithAscii(bytes, "GIF89a");
            case "image/webp" -> startsWithAscii(bytes, "RIFF") && hasAsciiAt(bytes, 8, "WEBP");
            case "image/avif" -> hasAsciiAt(bytes, 4, "ftyp")
                    && (hasAsciiAt(bytes, 8, "avif") || hasAsciiAt(bytes, 8, "avis"));
            case "video/mp4" -> hasAsciiAt(bytes, 4, "ftyp");
            case "video/webm" -> bytes.length >= 16
                    && startsWith(bytes, (byte) 0x1a, (byte) 0x45, (byte) 0xdf, (byte) 0xa3)
                    && hasWebmDocumentType(bytes);
            case "video/ogg" -> startsWithAscii(bytes, "OggS");
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte... expected) {
        if (bytes.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (bytes[index] != expected[index]) return false;
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] bytes, String expected) {
        return hasAsciiAt(bytes, 0, expected);
    }

    private static boolean hasAsciiAt(byte[] bytes, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expectedBytes.length) return false;
        for (int index = 0; index < expectedBytes.length; index++) {
            if (bytes[offset + index] != expectedBytes[index]) return false;
        }
        return true;
    }

    /**
     * WebM is an EBML container. The EBML magic alone is too weak to identify a
     * usable WebM file (a seven-byte magic prefix used to pass validation). A
     * real WebM header declares the DocType element (0x4282) with value
     * {@code webm}; inspect only a small header window rather than parsing or
     * buffering the whole video.
     */
    private static boolean hasWebmDocumentType(byte[] bytes) {
        int limit = Math.min(bytes.length, WEBM_HEADER_SCAN_LIMIT_BYTES);
        int headerLengthBytes = ebmlVariableLengthIntegerWidth(bytes[4]);
        int headerLength = readEbmlVariableLengthInteger(bytes, 4, limit);
        int headerStart = 4 + headerLengthBytes;
        if (headerLength < 0 || headerStart > limit - headerLength) return false;
        int headerEnd = headerStart + headerLength;
        for (int index = headerStart; index + 3 <= headerEnd; index++) {
            if (bytes[index] != 0x42 || bytes[index + 1] != (byte) 0x82) continue;
            int size = readEbmlVariableLengthInteger(bytes, index + 2, headerEnd);
            if (size != 4) continue;
            int lengthBytes = ebmlVariableLengthIntegerWidth(bytes[index + 2]);
            int valueOffset = index + 2 + lengthBytes;
            if (valueOffset + size <= headerEnd && hasAsciiAt(bytes, valueOffset, "webm")) return true;
        }
        return false;
    }

    private static int readEbmlVariableLengthInteger(byte[] bytes, int offset, int limit) {
        if (offset >= limit) return -1;
        int width = ebmlVariableLengthIntegerWidth(bytes[offset]);
        if (width == 0 || offset + width > limit) return -1;
        int marker = 1 << (8 - width);
        long value = Byte.toUnsignedInt(bytes[offset]) & (marker - 1);
        for (int index = 1; index < width; index++) {
            value = (value << 8) | Byte.toUnsignedInt(bytes[offset + index]);
        }
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    private static int ebmlVariableLengthIntegerWidth(byte value) {
        int unsigned = Byte.toUnsignedInt(value);
        for (int width = 1, marker = 0x80; width <= 8; width++, marker >>>= 1) {
            if ((unsigned & marker) != 0) return width;
        }
        return 0;
    }
}
