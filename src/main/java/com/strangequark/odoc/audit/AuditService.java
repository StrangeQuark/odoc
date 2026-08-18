package com.strangequark.odoc.audit;

import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.workspace.WorkspaceAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {
    private final AuditEventRepository events;
    private final WorkspaceAccessService access;
    AuditService(AuditEventRepository events, WorkspaceAccessService access) { this.events = events; this.access = access; }

    @Transactional(readOnly = true)
    AuditEventPageResponse page(UUID workspaceId, String cursor, int requestedLimit) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_AUDIT);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        Cursor before = decodeCursor(cursor);
        List<AuditEvent> page = events.findPage(workspaceId, before == null ? null : before.occurredAt(),
                before == null ? null : before.id(), PageRequest.of(0, limit + 1));
        boolean more = page.size() > limit;
        List<AuditEventResponse> items = page.stream().limit(limit).map(AuditEventResponse::from).toList();
        String next = more && !items.isEmpty() ? encodeCursor(items.getLast()) : null;
        return new AuditEventPageResponse(items, next);
    }
    private static String encodeCursor(AuditEventResponse event) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (event.occurredAt() + "|" + event.id()).getBytes(StandardCharsets.UTF_8));
    }
    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid audit cursor.");
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid audit cursor.", exception); }
    }
    private record Cursor(Instant occurredAt, UUID id) {}
}
