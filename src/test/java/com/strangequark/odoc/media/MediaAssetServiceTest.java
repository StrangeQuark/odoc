package com.strangequark.odoc.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.space.SpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MediaAssetServiceTest {
    @Mock private MediaAssetRepository assets;
    @Mock private SpaceRepository spaces;

    @Test
    void storesAnAllowedImageAndReturnsItsAuthenticatedApiPath() {
        UUID spaceId = UUID.randomUUID();
        MediaAssetService service = new MediaAssetService(assets, spaces,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC));
        MockMultipartFile file = new MockMultipartFile("file", "architecture.png", "image/png", new byte[] {1, 2, 3});
        when(spaces.existsById(spaceId)).thenReturn(true);
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAssetResponse response = service.upload(spaceId, file);

        assertThat(response.filename()).isEqualTo("architecture.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(3);
        assertThat(response.url()).isEqualTo("/api/v1/media/" + response.id());
    }
}
