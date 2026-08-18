package com.strangequark.odoc.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Mock private AuditEventRepository events;
    @Mock private WorkspaceAccessService access;

    @Test
    void cursorCarriesBothTimestampAndIdSoSameTimestampRowsAreNotSkipped() {
        UUID workspaceId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-08-18T15:00:00Z");
        AuditEvent first = event(workspaceId, UUID.fromString("00000000-0000-0000-0000-000000000002"), timestamp);
        AuditEvent second = event(workspaceId, UUID.fromString("00000000-0000-0000-0000-000000000001"), timestamp);
        when(events.findPage(eq(workspaceId), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        AuditEventPageResponse page = new AuditService(events, access).page(workspaceId, null, 1);

        assertThat(page.items()).containsExactly(AuditEventResponse.from(first));
        assertThat(page.nextCursor()).isNotBlank();
        new AuditService(events, access).page(workspaceId, page.nextCursor(), 1);
        ArgumentCaptor<UUID> cursorId = ArgumentCaptor.forClass(UUID.class);
        verify(events, org.mockito.Mockito.times(2)).findPage(eq(workspaceId), any(), cursorId.capture(), any(Pageable.class));
        assertThat(cursorId.getAllValues().getLast()).isEqualTo(first.id());
    }

    @Test
    void onlyTheExplicitRetentionServiceUsesTheDeleteQuery() {
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        when(events.deleteBefore(cutoff)).thenReturn(3);

        assertThat(new AuditRetentionService(events).purgeBefore(cutoff)).isEqualTo(3);
        verify(events).deleteBefore(cutoff);
    }

    private static AuditEvent event(UUID workspaceId, UUID id, Instant timestamp) {
        return new AuditEvent(id, workspaceId, UUID.randomUUID(), "workspace.updated", "workspace", workspaceId,
                "success", "request-1", "ciphertext", timestamp, UUID.randomUUID());
    }
}
