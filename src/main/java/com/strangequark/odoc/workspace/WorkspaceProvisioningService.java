package com.strangequark.odoc.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first owned workspace atomically with local-account enrollment. */
@Service
public class WorkspaceProvisioningService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final Clock clock;

    @Autowired
    WorkspaceProvisioningService(WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships) {
        this(workspaces, memberships, Clock.systemUTC());
    }

    WorkspaceProvisioningService(
            WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships, Clock clock) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.clock = clock;
    }

    @Transactional
    public UUID ensureOwnedWorkspace(UUID userId) {
        return memberships.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .findFirst()
                .map(WorkspaceMembership::workspaceId)
                .orElseGet(() -> createOwnedWorkspace(userId));
    }

    private UUID createOwnedWorkspace(UUID userId) {
        Instant now = clock.instant();
        UUID workspaceId = UUID.randomUUID();
        workspaces.save(new Workspace(workspaceId, "My workspace", now));
        memberships.save(new WorkspaceMembership(
                UUID.randomUUID(), workspaceId, userId, WorkspaceRole.OWNER, now));
        return workspaceId;
    }
}
