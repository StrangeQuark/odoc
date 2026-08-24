package com.strangequark.odoc.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock
    private SpaceRepository spaces;
    @Mock
    private WorkspaceAccessService workspaceAccess;

    @Test
    void normalizesTheSpaceKeyBeforePersisting() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        UUID workspaceId = UUID.randomUUID();
        SpaceService service = new SpaceService(spaces, workspaceAccess, Clock.fixed(now, ZoneOffset.UTC));
        when(workspaceAccess.defaultWorkspaceForCurrentUser()).thenReturn(workspaceId);
        when(spaces.findByWorkspaceIdAndKey(workspaceId, "ENG")).thenReturn(Optional.empty());
        when(spaces.saveAndFlush(any(Space.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SpaceResponse response = service.create(new CreateSpaceRequest("eng", "Engineering", "Team docs"));

        assertThat(response.key()).isEqualTo("ENG");
        assertThat(response.name()).isEqualTo("Engineering");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void getsUpdatesAndDeletesASpaceThroughTheRepository() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        UUID spaceId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Space space = new Space(spaceId, workspaceId, "ENG", "Engineering", "Old", Instant.EPOCH);
        SpaceService service = new SpaceService(spaces, workspaceAccess, Clock.fixed(now, ZoneOffset.UTC));
        when(spaces.findById(spaceId)).thenReturn(Optional.of(space));
        when(spaces.saveAndFlush(space)).thenReturn(space);

        assertThat(service.get(spaceId).name()).isEqualTo("Engineering");
        SpaceResponse updated = service.update(spaceId, new UpdateSpaceRequest(" Platform ", " Current docs "));
        service.delete(spaceId);

        assertThat(updated.name()).isEqualTo("Platform");
        assertThat(updated.description()).isEqualTo("Current docs");
        assertThat(updated.updatedAt()).isEqualTo(now);
        verify(spaces).delete(space);
    }
}
