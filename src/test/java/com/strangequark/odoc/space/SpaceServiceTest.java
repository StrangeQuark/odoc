package com.strangequark.odoc.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock
    private SpaceRepository spaces;

    @Test
    void normalizesTheSpaceKeyBeforePersisting() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        SpaceService service = new SpaceService(spaces, Clock.fixed(now, ZoneOffset.UTC));
        when(spaces.findByKey("ENG")).thenReturn(Optional.empty());
        when(spaces.saveAndFlush(any(Space.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceResponse response = service.create(new CreateSpaceRequest("eng", "Engineering", "Team docs"));

        assertThat(response.key()).isEqualTo("ENG");
        assertThat(response.name()).isEqualTo("Engineering");
        assertThat(response.createdAt()).isEqualTo(now);
    }
}
