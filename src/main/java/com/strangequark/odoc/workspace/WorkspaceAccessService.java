package com.strangequark.odoc.workspace;

import com.strangequark.odoc.auth.CurrentUser;
import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.authorization.WorkspaceAuthorizationService;
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
    private final WorkspaceAuthorizationService authorization;

    WorkspaceAccessService(
            CurrentUser currentUser,
            WorkspaceMembershipRepository memberships,
            WorkspaceProvisioningService provisioning,
            WorkspaceAuthorizationService authorization) {
        this.currentUser = currentUser;
        this.memberships = memberships;
        this.provisioning = provisioning;
        this.authorization = authorization;
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
                .filter(WorkspaceMembership::active)
                .map(WorkspaceMembership::workspaceId)
                .toList();
    }

    @Transactional(readOnly = true)
    WorkspaceMembership requireCurrentMembership(UUID workspaceId) {
        return memberships.findByWorkspaceIdAndUserId(workspaceId, currentUser.requireId())
                .filter(WorkspaceMembership::active)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
    }

    @Transactional(readOnly = true)
    void requireCurrentOwner(UUID workspaceId) {
        authorization.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_MEMBERS);
    }

    @Transactional(readOnly = true)
    public void requireAccessibleSpace(UUID spaceId) {
        authorization.requireSpaceAction(spaceId, AuthorizationAction.SPACE_VIEW);
    }

    @Transactional(readOnly = true)
    public boolean canAccessSpace(UUID spaceId) {
        return authorization.canAccessSpace(spaceId);
    }

    /** Compatibility façade while feature services migrate to the central policy model. */
    public void requireWorkspaceAction(UUID workspaceId, AuthorizationAction action) {
        authorization.requireWorkspaceAction(workspaceId, action);
    }

    /** Compatibility façade while feature services migrate to the central policy model. */
    public void requireSpaceAction(UUID spaceId, AuthorizationAction action) {
        authorization.requireSpaceAction(spaceId, action);
    }
}
