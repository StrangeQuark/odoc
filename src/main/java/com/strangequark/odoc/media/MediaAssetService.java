package com.strangequark.odoc.media;

import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedEncryptionException;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import com.strangequark.odoc.page.PageRepository;
import com.strangequark.odoc.page.PageVersionRepository;
import com.strangequark.odoc.space.SpaceRepository;
import com.strangequark.odoc.storage.ObjectStorage;
import com.strangequark.odoc.storage.ObjectStorageException;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.workspace.WorkspaceRepository;
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

/**
 * Deliberately bounded media service for the MVP. Browser uploads go through
 * this API, are encrypted before object storage, and never receive object-store
 * credentials. Large streaming, client-envelope uploads, and scanner services
 * are deferred until they solve a real product need.
 */
@Service
class MediaAssetService {
    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;
    static final int ORPHAN_CLEANUP_BATCH_SIZE = 100;
    private static final String OBJECT_PREFIX = "workspaces/";
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif");
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif",
            "video/mp4", "video/webm", "video/ogg");

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
    MediaAssetService(MediaAssetRepository assets, WorkspaceAccessService workspaceAccess,
            PageRepository pages, PageVersionRepository versions, SpaceRepository spaces,
            WorkspaceRepository workspaces, ObjectStorage objectStorage,
            ManagedRecordEncryption encryption) {
        this(assets, workspaceAccess, pages, versions, spaces, workspaces, objectStorage,
                encryption, Clock.systemUTC());
    }

    MediaAssetService(MediaAssetRepository assets, WorkspaceAccessService workspaceAccess,
            PageRepository pages, PageVersionRepository versions, SpaceRepository spaces,
            WorkspaceRepository workspaces, ObjectStorage objectStorage,
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
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded media.", exception);
        }
        validate(file.getOriginalFilename(), contentType, bytes);
        UUID workspaceScopeId = workspaces.findSecurityScopeIdById(workspaceIdForSpace(spaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        UUID assetId = UUID.randomUUID();
        String key = objectKey(workspaceScopeId, assetId);
        try {
            byte[] ciphertext = MediaEncryptedRecordCodec.encode(encryption.encrypt(
                    new EncryptionContext(new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceScopeId),
                            assetId, EncryptionPurpose.MEDIA, 1),
                    bytes));
            objectStorage.put(key, ciphertext, "application/octet-stream");
            String filename = safeFilename(file.getOriginalFilename());
            String filenameEnvelope = com.strangequark.odoc.jobs.EncryptedPayloadCodec.encode(encryption.encrypt(
                    new EncryptionContext(new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceScopeId),
                            assetId, EncryptionPurpose.MEDIA, 2, "metadata:filename"),
                    filename.getBytes(StandardCharsets.UTF_8)));
            MediaAsset asset = assets.save(new MediaAsset(assetId, spaceId, filenameEnvelope, contentType,
                    key, sha256(bytes), bytes.length, clock.instant()));
            return MediaAssetResponse.from(asset, filename);
        } catch (ObjectStorageException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is unavailable.", exception);
        } catch (RuntimeException exception) {
            bestEffortDelete(key);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    MediaContent download(UUID id) {
        MediaAsset asset = get(id);
        if (!asset.available()) throw notFound();
        UUID workspaceScopeId = workspaces.findSecurityScopeIdById(workspaceIdForSpace(asset.spaceId()))
                .orElseThrow(this::notFound);
        try {
            byte[] plaintext = asset.storedExternally()
                    ? encryption.decrypt(new EncryptionContext(
                            new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceScopeId), asset.id(),
                            EncryptionPurpose.MEDIA, 1),
                            MediaEncryptedRecordCodec.decode(objectStorage.get(asset.objectKey())))
                    : asset.content();
            if (plaintext == null || plaintext.length != asset.sizeBytes()
                    || !sha256(plaintext).equals(asset.contentSha256())) {
                throw notFound();
            }
            return new MediaContent(asset, plaintext, filenameFor(asset, workspaceScopeId));
        } catch (ObjectStorageException | ManagedEncryptionException | IllegalArgumentException exception) {
            throw notFound();
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    boolean deleteIfUnreferenced(UUID id) {
        MediaAsset asset = assets.findById(id).orElseThrow(this::notFound);
        workspaceAccess.requireSpaceAction(asset.spaceId(), AuthorizationAction.ATTACHMENT_DELETE);
        if (isReferenced(id)) return false;
        return delete(asset);
    }

    @Transactional
    int deleteUnreferencedOlderThan(Instant cutoff) {
        int removed = 0;
        for (MediaAssetRepository.MediaAssetStorageReference asset
                : assets.findStorageReferencesByCreatedAtBefore(cutoff,
                        PageRequest.of(0, ORPHAN_CLEANUP_BATCH_SIZE))) {
            if (!isReferenced(asset.getId()) && delete(asset)) removed++;
        }
        return removed;
    }

    private MediaAsset get(UUID id) {
        MediaAsset asset = assets.findById(id).orElseThrow(this::notFound);
        workspaceAccess.requireSpaceAction(asset.spaceId(), AuthorizationAction.ATTACHMENT_VIEW);
        return asset;
    }

    private boolean delete(MediaAsset asset) {
        asset.markDeletionPending();
        assets.save(asset);
        if (asset.storedExternally()) {
            try {
                objectStorage.delete(asset.objectKey());
            } catch (ObjectStorageException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Media storage is unavailable.", exception);
            }
        }
        return assets.deleteDirectlyById(asset.id()) > 0;
    }

    private boolean delete(MediaAssetRepository.MediaAssetStorageReference asset) {
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

    private UUID workspaceIdForSpace(UUID spaceId) {
        return spaces.findWorkspaceIdById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found."));
    }

    private String filenameFor(MediaAsset asset, UUID workspaceScopeId) {
        if (asset.filenameEnvelope() == null) return asset.filename();
        try {
            return new String(encryption.decrypt(new EncryptionContext(
                    new SecurityScope(SecurityScopeKind.WORKSPACE, workspaceScopeId), asset.id(),
                    EncryptionPurpose.MEDIA, 2, "metadata:filename"),
                    com.strangequark.odoc.jobs.EncryptedPayloadCodec.decode(asset.filenameEnvelope())),
                    StandardCharsets.UTF_8);
        } catch (ManagedEncryptionException | IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static void validate(String filename, String contentType, byte[] bytes) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a simple media filename.");
        }
        if (!SUPPORTED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Use a PNG, JPEG, GIF, WebP, AVIF, MP4, WebM, or Ogg media file.");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty media file.");
        }
        long limit = IMAGE_TYPES.contains(contentType) ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (bytes.length > limit) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "The selected media is larger than the MVP upload limit.");
        }
        if (!hasExpectedSignature(contentType, bytes)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The uploaded file does not match its declared media type.");
        }
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
            case "video/webm" -> startsWith(bytes, (byte) 0x1a, (byte) 0x45, (byte) 0xdf, (byte) 0xa3);
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

    private static String objectKey(UUID workspaceScopeId, UUID assetId) {
        return OBJECT_PREFIX + workspaceScopeId + "/media/" + assetId + "/payload.odm";
    }

    private static String safeFilename(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void bestEffortDelete(String key) {
        try {
            objectStorage.delete(key);
        } catch (ObjectStorageException ignored) {
            // The later orphan sweep can remove residue safely.
        }
    }

    private boolean isReferenced(UUID assetId) {
        String path = "/api/v1/media/" + assetId;
        return pages.existsByContentContaining(path) || versions.existsByContentContaining(path);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
    }

    record MediaContent(MediaAsset asset, byte[] content, String filename) {}
}
