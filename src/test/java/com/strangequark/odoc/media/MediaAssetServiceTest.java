package com.strangequark.odoc.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.page.PageRepository;
import com.strangequark.odoc.page.PageVersionRepository;
import com.strangequark.odoc.space.SpaceRepository;
import com.strangequark.odoc.storage.ObjectStorage;
import com.strangequark.odoc.encryption.EncryptedRecord;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
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
    private static final byte[] WEBM_EBML_HEADER = {
            (byte) 0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, (byte) 0x9f,
            0x42, (byte) 0x86, (byte) 0x81, 0x01,
            0x42, (byte) 0xf7, (byte) 0x81, 0x01,
            0x42, (byte) 0xf2, (byte) 0x81, 0x04,
            0x42, (byte) 0xf3, (byte) 0x81, 0x08,
            0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm',
            0x42, (byte) 0x87, (byte) 0x81, 0x02,
            0x42, (byte) 0x85, (byte) 0x81, 0x02,
            0x18, 0x53, (byte) 0x80, 0x67, (byte) 0xff
    };

    @Mock private MediaAssetRepository assets;
    @Mock private WorkspaceAccessService workspaceAccess;
    @Mock private PageRepository pages;
    @Mock private PageVersionRepository versions;
    @Mock private SpaceRepository spaces;
    @Mock private WorkspaceRepository workspaces;
    @Mock private ObjectStorage objectStorage;
    @Mock private ManagedRecordEncryption encryption;

    @Test
    void storesAnAllowedImageAndReturnsItsAuthenticatedApiPath() throws Exception {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = new MediaAssetService(assets, workspaceAccess, pages, versions, spaces, workspaces, objectStorage, encryption,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        MockMultipartFile file = new MockMultipartFile("file", "architecture.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3});
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        encryptionSetup(spaceId);

        MediaAssetResponse response = service.upload(spaceId, file);

        assertThat(response.filename()).isEqualTo("architecture.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(7);
        assertThat(response.url()).isEqualTo("/api/v1/media/" + response.id());
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorage).put(objectKey.capture(), payload.capture(), eq("application/octet-stream"));
        assertThat(objectKey.getValue()).contains("/media/" + response.id()).doesNotContain("architecture.png");
        assertThat(java.util.Arrays.equals(payload.getValue(), file.getBytes())).isFalse();
    }

    @Test
    void storesAnAllowedVideoWhenTheFileSignatureMatches() {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = new MediaAssetService(assets, workspaceAccess, pages, versions, spaces, workspaces, objectStorage, encryption,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        MockMultipartFile file = new MockMultipartFile("file", "walkthrough.webm", "video/webm",
                WEBM_EBML_HEADER);
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        encryptionSetup(spaceId);

        MediaAssetResponse response = service.upload(spaceId, file);

        assertThat(response.filename()).isEqualTo("walkthrough.webm");
        assertThat(response.contentType()).isEqualTo("video/webm");
        assertThat(response.sizeBytes()).isEqualTo(WEBM_EBML_HEADER.length);
    }

    @Test
    void rejectsAnEbmlMagicPrefixWithoutTheRequiredWebmDocumentType() {
        UUID spaceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "not-really-webm.webm", "video/webm",
                new byte[] {(byte) 0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1, 2, 3});

        assertThatThrownBy(() -> service().upload(spaceId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsMediaWhenTheDeclaredTypeDoesNotMatchTheBytes() {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = service();
        MockMultipartFile file = new MockMultipartFile("file", "not-an-image.png", "image/png",
                "not actually png".getBytes());

        assertThatThrownBy(() -> service.upload(spaceId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsMediaWithoutADeclaredContentTypeInsteadOfFailingServerSide() {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = service();
        MockMultipartFile file = new MockMultipartFile("file", "unknown", null,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});

        assertThatThrownBy(() -> service.upload(spaceId, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PNG, JPEG");
    }

    @Test
    void deletesAnUnusedUploadButPreservesMediaReferencedByPageHistory() {
        UUID unusedId = UUID.randomUUID();
        when(assets.findById(unusedId)).thenReturn(java.util.Optional.of(asset(unusedId)));
        when(pages.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(versions.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(assets.deleteDirectlyById(unusedId)).thenReturn(1);

        assertThat(service().deleteIfUnreferenced(unusedId)).isTrue();
        verify(assets).deleteDirectlyById(unusedId);

        UUID referencedId = UUID.randomUUID();
        when(assets.findById(referencedId)).thenReturn(java.util.Optional.of(asset(referencedId)));
        when(pages.existsByContentContaining("/api/v1/media/" + referencedId)).thenReturn(false);
        when(versions.existsByContentContaining("/api/v1/media/" + referencedId)).thenReturn(true);

        assertThat(service().deleteIfUnreferenced(referencedId)).isFalse();
        verify(assets, never()).deleteDirectlyById(referencedId);
    }

    @Test
    void sweepsBoundedAssetIdsWithoutMaterializingMediaBlobs() {
        Instant cutoff = Instant.parse("2026-08-21T00:00:00Z");
        UUID unusedId = UUID.randomUUID();
        UUID referencedId = UUID.randomUUID();
        when(assets.findStorageReferencesByCreatedAtBefore(eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(reference(unusedId), reference(referencedId)));
        when(pages.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(versions.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(assets.deleteDirectlyById(unusedId)).thenReturn(1);
        assertThat(service().deleteUnreferencedOlderThan(cutoff)).isEqualTo(1);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(assets).findStorageReferencesByCreatedAtBefore(eq(cutoff), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(MediaAssetService.ORPHAN_CLEANUP_BATCH_SIZE);
        verify(assets, never()).findById(any());
    }

    @Test
    void decryptsAVerifiedExternalObjectOnlyAfterTheAuthorizedAssetLookup() {
        UUID spaceId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID workspaceScopeId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1};
        MediaAsset asset = new MediaAsset(assetId, spaceId, "diagram.png", "image/png",
                "workspaces/" + workspaceScopeId + "/media/" + assetId + "/payload.odm",
                sha256(bytes), bytes.length, Instant.EPOCH);
        when(assets.findById(assetId)).thenReturn(java.util.Optional.of(asset));
        when(spaces.findWorkspaceIdById(spaceId)).thenReturn(java.util.Optional.of(workspaceId));
        when(workspaces.findSecurityScopeIdById(workspaceId)).thenReturn(java.util.Optional.of(workspaceScopeId));
        when(objectStorage.get(asset.objectKey())).thenReturn(MediaEncryptedRecordCodec.encode(encryptedRecord()));
        when(encryption.decrypt(any(), any())).thenReturn(bytes);

        assertThat(service().download(assetId).content()).isEqualTo(bytes);

        verify(workspaceAccess).requireSpaceAction(spaceId,
                com.strangequark.odoc.authorization.AuthorizationAction.ATTACHMENT_VIEW);
    }

    private MediaAssetService service() {
        return new MediaAssetService(assets, workspaceAccess, pages, versions, spaces, workspaces, objectStorage, encryption, Clock.systemUTC());
    }

    private static MediaAsset asset(UUID id) {
        return new MediaAsset(id, UUID.randomUUID(), "media.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}, Instant.EPOCH);
    }

    private void encryptionSetup(UUID spaceId) {
        UUID workspaceId = UUID.randomUUID();
        when(spaces.findWorkspaceIdById(spaceId)).thenReturn(java.util.Optional.of(workspaceId));
        when(workspaces.findSecurityScopeIdById(workspaceId)).thenReturn(java.util.Optional.of(UUID.randomUUID()));
        when(encryption.encrypt(any(), any())).thenReturn(encryptedRecord());
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
