package com.strangequark.odoc.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.auth.AuthenticatedUser;
import com.strangequark.odoc.auth.CurrentUser;
import com.strangequark.odoc.space.SpaceRepository;
import com.strangequark.odoc.workspace.WorkspaceMembership;
import com.strangequark.odoc.workspace.WorkspaceMembershipRepository;
import com.strangequark.odoc.workspace.WorkspaceRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkspaceAuthorizationServiceTest {
    @Mock private CurrentUser currentUser;
    @Mock private WorkspaceMembershipRepository memberships;
    @Mock private SpaceRepository spaces;

    @Test
    void hidesCrossWorkspaceSpaceFromAnAuthenticatedMember() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID otherWorkspace = UUID.randomUUID();
        WorkspaceAuthorizationService service = service();
        when(currentUser.require()).thenReturn(user(userId));
        when(spaces.findWorkspaceIdById(spaceId)).thenReturn(Optional.of(otherWorkspace));
        when(memberships.findByWorkspaceIdAndUserId(otherWorkspace, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireSpaceAction(spaceId, AuthorizationAction.PAGE_VIEW))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void letsAnActiveMemberEditPagesButNotAdministerAWorkspace() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        WorkspaceMembership membership = org.mockito.Mockito.mock(WorkspaceMembership.class);
        when(membership.userId()).thenReturn(userId);
        when(membership.role()).thenReturn(WorkspaceRole.MEMBER);
        when(membership.active()).thenReturn(true);
        WorkspaceAuthorizationService service = service();
        when(currentUser.require()).thenReturn(user(userId));
        when(memberships.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        service.requireWorkspaceAction(workspaceId, AuthorizationAction.PAGE_EDIT);
        assertThatThrownBy(() -> service.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_MEMBERS))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private WorkspaceAuthorizationService service() {
        return new WorkspaceAuthorizationService(currentUser, memberships, spaces, new WorkspacePermissionEvaluator());
    }

    private static AuthenticatedUser user(UUID id) {
        return new AuthenticatedUser(id, "member@example.test", true, Instant.parse("2026-08-18T00:00:00Z"));
    }
}
