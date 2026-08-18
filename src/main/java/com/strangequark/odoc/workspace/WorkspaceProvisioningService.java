package com.strangequark.odoc.workspace;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first owned workspace atomically with local-account enrollment. */
@Service
public class WorkspaceProvisioningService {
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceDomainService domain;

    @Autowired
    WorkspaceProvisioningService(WorkspaceMembershipRepository memberships, WorkspaceDomainService domain) {
        this.memberships = memberships;
        this.domain = domain;
    }

    @Transactional
    public UUID ensureOwnedWorkspace(UUID userId) {
        return memberships.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .findFirst()
                .map(WorkspaceMembership::workspaceId)
                .orElseGet(() -> domain.createOwnedWorkspace(userId, "My workspace"));
    }
}
