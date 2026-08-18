package com.strangequark.odoc.authorization;

import com.strangequark.odoc.workspace.WorkspaceRole;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pure default-deny authorization matrix for resources owned by a workspace. Restrictions,
 * shares, service scopes, and space overrides plug in before this final decision in later phases.
 */
@Component
public class WorkspacePermissionEvaluator {
    private static final Set<AuthorizationAction> MEMBER_ACTIONS = EnumSet.of(
            AuthorizationAction.WORKSPACE_VIEW,
            AuthorizationAction.SPACE_CREATE,
            AuthorizationAction.SPACE_VIEW,
            AuthorizationAction.PAGE_CREATE,
            AuthorizationAction.PAGE_VIEW,
            AuthorizationAction.PAGE_EDIT,
            AuthorizationAction.PAGE_COMMENT,
            AuthorizationAction.PAGE_PUBLISH,
            AuthorizationAction.ATTACHMENT_UPLOAD,
            AuthorizationAction.ATTACHMENT_VIEW,
            AuthorizationAction.ATTACHMENT_DOWNLOAD,
            AuthorizationAction.REPOSITORY_VIEW,
            AuthorizationAction.TEMPLATE_VIEW,
            AuthorizationAction.TEMPLATE_USE,
            AuthorizationAction.NOTIFICATION_VIEW);
    private static final Set<AuthorizationAction> GUEST_ACTIONS = EnumSet.of(
            AuthorizationAction.WORKSPACE_VIEW,
            AuthorizationAction.SPACE_VIEW,
            AuthorizationAction.PAGE_VIEW,
            AuthorizationAction.ATTACHMENT_VIEW,
            AuthorizationAction.ATTACHMENT_DOWNLOAD,
            AuthorizationAction.REPOSITORY_VIEW,
            AuthorizationAction.TEMPLATE_VIEW,
            AuthorizationAction.NOTIFICATION_VIEW);

    public AuthorizationDecision decide(AuthorizationPrincipal principal, AuthorizationAction action) {
        if (principal == null || principal.userId() == null) return AuthorizationDecision.deny("unauthenticated");
        if (!principal.active()) return AuthorizationDecision.deny("deactivated");
        if (principal.instanceAdmin()) return AuthorizationDecision.allow("instance_admin");
        if (principal.workspaceRole() == WorkspaceRole.OWNER) return AuthorizationDecision.allow("workspace_owner");
        if (principal.workspaceRole() == WorkspaceRole.MEMBER && MEMBER_ACTIONS.contains(action)) {
            return AuthorizationDecision.allow("workspace_member");
        }
        if (principal.workspaceRole() == null && GUEST_ACTIONS.contains(action)) {
            return AuthorizationDecision.allow("workspace_guest");
        }
        return AuthorizationDecision.deny("role_action_denied");
    }
}
