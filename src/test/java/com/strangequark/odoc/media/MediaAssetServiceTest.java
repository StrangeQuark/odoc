package com.strangequark.odoc.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.encryption.EncryptedRecord;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.page.PageRepository;
import com.strangequark.odoc.page.PageVersionRepository;
import com.strangequark.odoc.space.SpaceRepository;
import com.strangequark.odoc.storage.ObjectStorage;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import com.strangequark.odoc.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MediaAssetServiceTest {
    @Mock private MediaAssetRepository assets;
    @Mock private WorkspaceAccessService workspaceAccess;
    @Mock private PageRepository pages;
    @Mock private PageVersionRepository versions;
    @Mock private SpaceRepository spaces;
    @Mock private WorkspaceRepository workspaces;
    @Mock private ObjectStorage objectStorage;
    @Mock private ManagedRecordEncryption encryption;

    @Test
    void encryptsAndStoresAnAllowedImageWithoutLeakingItsFilenameIntoTheObjectKey() throws Exception {
        UUID spaceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "architecture.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3});
        setupUploadScope(spaceId);
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAssetResponse response = service().upload(spaceId, file);

        assertThat(response.filename()).isEqualTo("architecture.png");
        assertThat(response.url()).isEqualTo("/api/v1/media/" + response.id());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorage).put(key.capture(), payload.capture(), eq("application/octet-stream"));
        assertThat(key.getValue()).contains("/media/" + response.id()).doesNotContain("architecture.png");
        assertThat(payload.getValue()).isNotEqualTo(file.getBytes());
        verify(workspaceAccess).requireSpaceAction(spaceId,
                com.strangequark.odoc.authorization.AuthorizationAction.ATTACHMENT_UPLOAD);
    }

    @Test
    void rejectsEmptyUnsupportedAndOversizeUploadsBeforeWritingToStorage() {
        assertThatThrownBy(() -> service().upload(UUID.randomUUID(),
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("non-empty");

        assertThatThrownBy(() -> service().upload(UUID.randomUUID(),
                new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[] {1, 2, 3, 4})))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PNG, JPEG");

        byte[] tooLarge = new byte[(int) MediaAssetService.MAX_IMAGE_BYTES + 1];
        tooLarge[0] = (byte) 0x89;
        tooLarge[1] = 0x50;
        tooLarge[2] = 0x4e;
        tooLarge[3] = 0x47;
        assertThatThrownBy(() -> service().upload(UUID.randomUUID(),
                new MockMultipartFile("file", "large.png", "image/png", tooLarge)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MVP upload limit");

        verify(objectStorage, never()).put(any(), any(), any());
    }

    @Test
    void rejectsAFileWhoseBytesDoNotMatchItsDeclaredType() {
        MockMultipartFile file = new MockMultipartFile("file", "not-an-image.png", "image/png",
                "not actually a PNG".getBytes());

        assertThatThrownBy(() -> service().upload(UUID.randomUUID(), file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void decryptsAStoredAssetOnlyAfterAuthorizingTheSpace() {
        UUID spaceId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1};
        MediaAsset asset = new MediaAsset(assetId, spaceId, "diagram.png", "image/png",
                "workspaces/" + scopeId + "/media/" + assetId + "/payload.odm", sha256(bytes), bytes.length,
                Instant.EPOCH);
        when(assets.findById(assetId)).thenReturn(java.util.Optional.of(asset));
        when(spaces.findWorkspaceIdById(spaceId)).thenReturn(java.util.Optional.of(workspaceId));
        when(workspaces.findSecurityScopeIdById(workspaceId)).thenReturn(java.util.Optional.of(scopeId));
        when(objectStorage.get(asset.objectKey())).thenReturn(MediaEncryptedRecordCodec.encode(encryptedRecord()));
        when(encryption.decrypt(any(), any())).thenReturn(bytes);

        MediaAssetService.MediaContent content = service().download(assetId);

        assertThat(content.content()).isEqualTo(bytes);
        assertThat(content.filename()).isEqualTo("diagram.png");
        verify(workspaceAccess).requireSpaceAction(spaceId,
                com.strangequark.odoc.authorization.AuthorizationAction.ATTACHMENT_VIEW);
    }

    @Test
    void deletesAnUnreferencedAssetButKeepsMediaUsedByPageHistory() {
        UUID unusedId = UUID.randomUUID();
        when(assets.findById(unusedId)).thenReturn(java.util.Optional.of(asset(unusedId)));
        when(pages.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(versions.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(assets.deleteDirectlyById(unusedId)).thenReturn(1);

        assertThat(service().deleteIfUnreferenced(unusedId)).isTrue();
        verify(assets).deleteDirectlyById(unusedId);

        UUID referencedId = UUID.randomUUID();
        when(assets.findById(referencedId)).thenReturn(java.util.Optional.of(asset(referencedId)));
        when(pages.existsByContentContaining("/api/v1/media/" + referencedId)).thenReturn(true);

        assertThat(service().deleteIfUnreferenced(referencedId)).isFalse();
        verify(assets, never()).deleteDirectlyById(referencedId);
    }

    @Test
    void sweepsACompactBatchOfUnreferencedAssets() {
        Instant cutoff = Instant.parse("2026-08-21T00:00:00Z");
        UUID assetId = UUID.randomUUID();
        when(assets.findStorageReferencesByCreatedAtBefore(eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(reference(assetId)));
        when(pages.existsByContentContaining("/api/v1/media/" + assetId)).thenReturn(false);
        when(versions.existsByContentContaining("/api/v1/media/" + assetId)).thenReturn(false);
        when(assets.deleteDirectlyById(assetId)).thenReturn(1);

        assertThat(service().deleteUnreferencedOlderThan(cutoff)).isEqualTo(1);
        verify(assets).deleteDirectlyById(assetId);
    }

    private MediaAssetService service() {
        return new MediaAssetService(assets, workspaceAccess, pages, versions, spaces, workspaces, objectStorage,
                encryption, Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC));
    }

    private void setupUploadScope(UUID spaceId) {
        UUID workspaceId = UUID.randomUUID();
        when(spaces.findWorkspaceIdById(spaceId)).thenReturn(java.util.Optional.of(workspaceId));
        when(workspaces.findSecurityScopeIdById(workspaceId)).thenReturn(java.util.Optional.of(UUID.randomUUID()));
        when(encryption.encrypt(any(), any())).thenReturn(encryptedRecord());
    }

    private static MediaAsset asset(UUID id) {
        return new MediaAsset(id, UUID.randomUUID(), "media.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}, Instant.EPOCH);
    }

    private static EncryptedRecord encryptedRecord() {
        return new EncryptedRecord(1, EncryptedRecord.ALGORITHM, 1, new byte[12], new byte[16]);
    }

    private static MediaAssetRepository.MediaAssetStorageReference reference(UUID id) {
        return new MediaAssetRepository.MediaAssetStorageReference() {
            @Override public UUID getId() { return id; }
            @Override public String getObjectKey() { return null; }
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
