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
import com.strangequark.odoc.workspace.WorkspaceAccessService;
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

    @Test
    void storesAnAllowedImageAndReturnsItsAuthenticatedApiPath() {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = new MediaAssetService(assets, workspaceAccess, pages, versions,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        MockMultipartFile file = new MockMultipartFile("file", "architecture.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3});
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAssetResponse response = service.upload(spaceId, file);

        assertThat(response.filename()).isEqualTo("architecture.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(7);
        assertThat(response.url()).isEqualTo("/api/v1/media/" + response.id());
    }

    @Test
    void storesAnAllowedVideoWhenTheFileSignatureMatches() {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = new MediaAssetService(assets, workspaceAccess, pages, versions,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        MockMultipartFile file = new MockMultipartFile("file", "walkthrough.webm", "video/webm",
                WEBM_EBML_HEADER);
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(assets.findIdsByCreatedAtBefore(eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(unusedId, referencedId));
        when(pages.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(versions.existsByContentContaining("/api/v1/media/" + unusedId)).thenReturn(false);
        when(assets.deleteDirectlyById(unusedId)).thenReturn(1);
        when(pages.existsByContentContaining("/api/v1/media/" + referencedId)).thenReturn(true);

        assertThat(service().deleteUnreferencedOlderThan(cutoff)).isEqualTo(1);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(assets).findIdsByCreatedAtBefore(eq(cutoff), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(MediaAssetService.ORPHAN_CLEANUP_BATCH_SIZE);
        verify(assets, never()).findById(any());
    }

    private MediaAssetService service() {
        return new MediaAssetService(assets, workspaceAccess, pages, versions, Clock.systemUTC());
    }

    private static MediaAsset asset(UUID id) {
        return new MediaAsset(id, UUID.randomUUID(), "media.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}, Instant.EPOCH);
    }

}
