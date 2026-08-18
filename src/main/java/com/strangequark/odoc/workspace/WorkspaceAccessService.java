package com.strangequark.odoc.workspace;

import com.strangequark.odoc.auth.CurrentUser;
import com.strangequark.odoc.space.SpaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Applies membership scoping before feature services expose a workspace-owned resource. */
@Service
public class WorkspaceAccessService {
    private final CurrentUser currentUser;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceProvisioningService provisioning;
    private final SpaceRepository spaces;

    WorkspaceAccessService(
            CurrentUser currentUser,
            WorkspaceMembershipRepository memberships,
            WorkspaceProvisioningService provisioning,
            SpaceRepository spaces) {
        this.currentUser = currentUser;
        this.memberships = memberships;
        this.provisioning = provisioning;
        this.spaces = spaces;
    }

    @Transactional
    public UUID defaultWorkspaceForCurrentUser() {
        return provisioning.ensureOwnedWorkspace(currentUser.requireId());
    }

    public java.util.UUID currentUserId() {
        return currentUser.requireId();
    }

    public String currentUserEmail() {
        return currentUser.require().email();
    }

    @Transactional(readOnly = true)
    public List<UUID> workspaceIdsForCurrentUser() {
        return memberships.findAllByUserIdOrderByCreatedAtAsc(currentUser.requireId()).stream()
                .map(WorkspaceMembership::workspaceId)
                .toList();
    }

    @Transactional(readOnly = true)
    WorkspaceMembership requireCurrentMembership(UUID workspaceId) {
        return memberships.findByWorkspaceIdAndUserId(workspaceId, currentUser.requireId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
    }

    @Transactional(readOnly = true)
    void requireCurrentOwner(UUID workspaceId) {
        if (requireCurrentMembership(workspaceId).role() != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a workspace owner can manage members.");
        }
    }

    @Transactional(readOnly = true)
    public void requireAccessibleSpace(UUID spaceId) {
        if (!canAccessSpace(spaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found.");
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessSpace(UUID spaceId) {
        List<UUID> workspaceIds = workspaceIdsForCurrentUser();
        return !workspaceIds.isEmpty() && spaces.existsByIdAndWorkspaceIdIn(spaceId, workspaceIds);
    }
}
