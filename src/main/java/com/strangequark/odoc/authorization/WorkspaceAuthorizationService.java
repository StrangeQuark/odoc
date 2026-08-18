package com.strangequark.odoc.authorization;

import com.strangequark.odoc.auth.AuthenticatedUser;
import com.strangequark.odoc.auth.CurrentUser;
import com.strangequark.odoc.space.SpaceRepository;
import com.strangequark.odoc.workspace.WorkspaceMembership;
import com.strangequark.odoc.workspace.WorkspaceMembershipRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Resolves a server-derived principal and turns matrix denials into non-enumerating responses. */
@Service
public class WorkspaceAuthorizationService {
    private final CurrentUser currentUser;
    private final WorkspaceMembershipRepository memberships;
    private final SpaceRepository spaces;
    private final WorkspacePermissionEvaluator evaluator;

    WorkspaceAuthorizationService(
            CurrentUser currentUser,
            WorkspaceMembershipRepository memberships,
            SpaceRepository spaces,
            WorkspacePermissionEvaluator evaluator) {
        this.currentUser = currentUser;
        this.memberships = memberships;
        this.spaces = spaces;
        this.evaluator = evaluator;
    }

    @Transactional(readOnly = true)
    public void requireWorkspaceAction(UUID workspaceId, AuthorizationAction action) {
        AuthenticatedUser user = currentUser.require();
        AuthorizationPrincipal principal = memberships.findByWorkspaceIdAndUserId(workspaceId, user.id())
                .map(this::principal)
                .orElseGet(AuthorizationPrincipal::anonymous);
        require(evaluator.decide(principal, action));
    }

    @Transactional(readOnly = true)
    public void requireSpaceAction(UUID spaceId, AuthorizationAction action) {
        UUID workspaceId = spaces.findWorkspaceIdById(spaceId)
                .orElseThrow(() -> hidden());
        try {
            requireWorkspaceAction(workspaceId, action);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN) throw hidden();
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessSpace(UUID spaceId) {
        try {
            requireSpaceAction(spaceId, AuthorizationAction.SPACE_VIEW);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private AuthorizationPrincipal principal(WorkspaceMembership membership) {
        return new AuthorizationPrincipal(
                membership.userId(), membership.role(), membership.active(), false);
    }

    private static void require(AuthorizationDecision decision) {
        if (!decision.allowed()) throw hidden();
    }

    private static ResponseStatusException hidden() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
