package com.strangequark.odoc.media;

import com.strangequark.odoc.page.PageRepository;
import com.strangequark.odoc.page.PageVersionRepository;
import com.strangequark.odoc.space.SpaceRepository;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedEncryptionException;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import com.strangequark.odoc.storage.ObjectStorage;
import com.strangequark.odoc.storage.ObjectStorageException;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.workspace.WorkspaceRepository;
import com.strangequark.odoc.authorization.AuthorizationAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String OBJECT_PREFIX = "workspaces/";
    private static final java.util.regex.Pattern OWNED_OBJECT_KEY = java.util.regex.Pattern.compile(
            "^workspaces/[0-9a-fA-F-]{36}/media/[0-9a-fA-F-]{36}/payload\\.odm$");
    private static final int WEBM_HEADER_SCAN_LIMIT_BYTES = 4 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif");
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif", "video/mp4", "video/webm", "video/ogg");
    private final MediaAssetRepository assets;
    private final WorkspaceAccessService workspaceAccess;
    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final SpaceRepository spaces;
    private final WorkspaceRepository workspaces;
    private final ObjectStorage objectStorage;
    private final ManagedRecordEncryption encryption;
    private final Clock clock;

    @Autowired
    MediaAssetService(MediaAssetRepository assets, WorkspaceAccessService workspaceAccess, PageRepository pages,
            PageVersionRepository versions, SpaceRepository spaces, WorkspaceRepository workspaces, ObjectStorage objectStorage,
            ManagedRecordEncryption encryption) {
        this(assets, workspaceAccess, pages, versions, spaces, workspaces, objectStorage, encryption, Clock.systemUTC());
    }

    MediaAssetService(MediaAssetRepository assets, WorkspaceAccessService workspaceAccess, PageRepository pages,
            PageVersionRepository versions, SpaceRepository spaces, WorkspaceRepository workspaces, ObjectStorage objectStorage,
            ManagedRecordEncryption encryption, Clock clock) {
        this.assets = assets;
        this.workspaceAccess = workspaceAccess;
        this.pages = pages;
        this.versions = versions;
        this.spaces = spaces;
        this.workspaces = workspaces;
        this.objectStorage = objectStorage;
        this.encryption = encryption;
        this.clock = clock;
    }

    @Transactional
    MediaAssetResponse upload(UUID spaceId, MultipartFile file) {
        workspaceAccess.requireSpaceAction(spaceId, AuthorizationAction.ATTACHMENT_UPLOAD);
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || !SUPPORTED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Use a PNG, JPEG, GIF, WebP, AVIF, MP4, WebM, or Ogg media file.");
        }
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty media file.");
        long limit = IMAGE_TYPES.contains(contentType) ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (file.getSize() > limit) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    (IMAGE_TYPES.contains(contentType) ? "Images" : "Videos") + " must be " + limit / 1024 / 1024 + " MB or smaller.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasExpectedSignature(contentType, bytes)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "The uploaded file does not match its declared media type.");
            }
            UUID workspaceId = workspaces.findSecurityScopeIdById(workspaceIdForSpace(spaceId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
            UUID assetId = UUID.randomUUID();
            String objectKey = objectKey(workspaceId, assetId);
            byte[] ciphertext = MediaEncryptedRecordCodec.encode(encryption.encrypt(
                    new EncryptionContext(new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceId), assetId,
                            EncryptionPurpose.MEDIA, 1), bytes));
            try {
                objectStorage.put(objectKey, ciphertext, "application/octet-stream");
            } catch (ObjectStorageException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is unavailable.", exception);
            }
            MediaAsset asset = new MediaAsset(assetId, spaceId, safeFilename(file.getOriginalFilename()),
                    contentType, objectKey, sha256(bytes), bytes.length, clock.instant());
            try {
                asset = assets.save(asset);
            } catch (RuntimeException exception) {
                bestEffortDelete(objectKey);
                throw exception;
            }
            return MediaAssetResponse.from(asset);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded media.", exception);
        }
    }

    @Transactional(readOnly = true)
    MediaAsset get(UUID id) {
        MediaAsset asset = assets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found."));
        workspaceAccess.requireSpaceAction(asset.spaceId(), AuthorizationAction.ATTACHMENT_VIEW);
        return asset;
    }

    @Transactional(readOnly = true)
    MediaContent download(UUID id) {
        MediaAsset asset = get(id);
        if (!asset.available()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        byte[] content = asset.storedExternally() ? downloadExternal(asset) : asset.content();
        if (content == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        return new MediaContent(asset, content);
    }

    @Transactional
    boolean deleteIfUnreferenced(UUID id) {
        MediaAsset asset = assets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found."));
        workspaceAccess.requireSpaceAction(asset.spaceId(), AuthorizationAction.ATTACHMENT_DELETE);
        if (isReferenced(id)) return false;
        return deleteAsset(asset);
    }

    /**
     * A page version deliberately retains its referenced media. This delayed
     * sweep only removes abandoned uploads after the user has had ample time
     * to publish a long-running edit.
     */
    @Transactional
    int deleteUnreferencedOlderThan(Instant cutoff) {
        int removed = 0;
        for (MediaAssetRepository.MediaAssetStorageReference asset : assets.findStorageReferencesByCreatedAtBefore(cutoff,
                PageRequest.of(0, ORPHAN_CLEANUP_BATCH_SIZE))) {
            if (!isReferenced(asset.getId()) && deleteStorageReference(asset)) {
                removed++;
            }
        }
        removed += deleteUnknownObjectsOlderThan(cutoff);
        return removed;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "media";
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private UUID workspaceIdForSpace(UUID spaceId) {
        return spaces.findWorkspaceIdById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found."));
    }

    private static String objectKey(UUID workspaceSecurityScopeId, UUID assetId) {
        return "workspaces/" + workspaceSecurityScopeId + "/media/" + assetId + "/payload.odm";
    }

    private byte[] downloadExternal(MediaAsset asset) {
        try {
            UUID workspaceScopeId = workspaces.findSecurityScopeIdById(workspaceIdForSpace(asset.spaceId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
            byte[] plaintext = encryption.decrypt(new EncryptionContext(
                    new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceScopeId), asset.id(),
                    EncryptionPurpose.MEDIA, 1), MediaEncryptedRecordCodec.decode(objectStorage.get(asset.objectKey())));
            if (plaintext.length != asset.sizeBytes() || !sha256(plaintext).equals(asset.contentSha256())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
            }
            return plaintext;
        } catch (ObjectStorageException | ManagedEncryptionException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        }
    }

    private boolean deleteAsset(MediaAsset asset) {
        asset.markDeletionPending();
        assets.save(asset);
        if (asset.storedExternally()) {
            try {
                objectStorage.delete(asset.objectKey());
            } catch (ObjectStorageException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is unavailable.", exception);
            }
        }
        return assets.deleteDirectlyById(asset.id()) > 0;
    }

    private boolean deleteStorageReference(MediaAssetRepository.MediaAssetStorageReference asset) {
        assets.markDeletionPending(asset.getId());
        if (asset.getObjectKey() != null) {
            try {
                objectStorage.delete(asset.getObjectKey());
            } catch (ObjectStorageException exception) {
                return false;
            }
        }
        return assets.deleteDirectlyById(asset.getId()) > 0;
    }

    private int deleteUnknownObjectsOlderThan(Instant cutoff) {
        try {
            int removed = 0;
            for (ObjectStorage.StoredObject object : objectStorage.list(OBJECT_PREFIX, ORPHAN_CLEANUP_BATCH_SIZE)) {
                if (object.lastModified() != null && object.lastModified().isBefore(cutoff)
                        && OWNED_OBJECT_KEY.matcher(object.key()).matches() && !assets.existsByObjectKey(object.key())) {
                    objectStorage.delete(object.key());
                    removed++;
                }
            }
            return removed;
        } catch (ObjectStorageException exception) {
            return 0;
        }
    }

    private void bestEffortDelete(String objectKey) {
        try { objectStorage.delete(objectKey); } catch (ObjectStorageException ignored) { /* scheduled cleanup reconciles metadata */ }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    record MediaContent(MediaAsset asset, byte[] content) {}

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
