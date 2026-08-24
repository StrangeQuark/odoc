package com.strangequark.odoc.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.strangequark.odoc.workspace.WorkspaceRole;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The documented P1-104 role/action matrix is executable rather than prose-only. */
class WorkspacePermissionEvaluatorTest {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final WorkspacePermissionEvaluator EVALUATOR = new WorkspacePermissionEvaluator();

    @ParameterizedTest(name = "{0} owner={1} member={2} guest={3}")
    @MethodSource("matrix")
    void evaluatesEveryWorkspaceAction(
            AuthorizationAction action, boolean ownerAllowed, boolean memberAllowed, boolean guestAllowed) {
        assertThat(EVALUATOR.decide(new AuthorizationPrincipal(USER, WorkspaceRole.OWNER, true, false), action).allowed())
                .isEqualTo(ownerAllowed);
        assertThat(EVALUATOR.decide(new AuthorizationPrincipal(USER, WorkspaceRole.MEMBER, true, false), action).allowed())
                .isEqualTo(memberAllowed);
        assertThat(EVALUATOR.decide(AuthorizationPrincipal.guest(USER), action).allowed())
                .isEqualTo(guestAllowed);
        assertThat(EVALUATOR.decide(new AuthorizationPrincipal(USER, null, false, false), action).allowed()).isFalse();
        assertThat(EVALUATOR.decide(AuthorizationPrincipal.anonymous(), action).allowed()).isFalse();
        assertThat(EVALUATOR.decide(new AuthorizationPrincipal(USER, null, true, true), action).allowed()).isTrue();
    }

    private static Stream<Arguments> matrix() {
        EnumSet<AuthorizationAction> memberActions = EnumSet.of(
                AuthorizationAction.WORKSPACE_VIEW,
                AuthorizationAction.SPACE_CREATE,
                AuthorizationAction.SPACE_VIEW,
                AuthorizationAction.PAGE_CREATE,
                AuthorizationAction.PAGE_VIEW,
                AuthorizationAction.PAGE_EDIT,
                AuthorizationAction.PAGE_MOVE,
                AuthorizationAction.PAGE_COMMENT,
                AuthorizationAction.PAGE_PUBLISH,
                AuthorizationAction.ATTACHMENT_UPLOAD,
                AuthorizationAction.ATTACHMENT_VIEW,
                AuthorizationAction.ATTACHMENT_DOWNLOAD,
                AuthorizationAction.REPOSITORY_CONNECT,
                AuthorizationAction.REPOSITORY_VIEW,
                AuthorizationAction.TEMPLATE_VIEW,
                AuthorizationAction.TEMPLATE_USE,
                AuthorizationAction.NOTIFICATION_VIEW);
        EnumSet<AuthorizationAction> guestActions = EnumSet.of(
                AuthorizationAction.WORKSPACE_VIEW,
                AuthorizationAction.SPACE_VIEW,
                AuthorizationAction.PAGE_VIEW,
                AuthorizationAction.ATTACHMENT_VIEW,
                AuthorizationAction.ATTACHMENT_DOWNLOAD,
                AuthorizationAction.REPOSITORY_VIEW,
                AuthorizationAction.TEMPLATE_VIEW,
                AuthorizationAction.NOTIFICATION_VIEW);
        return Stream.of(AuthorizationAction.values())
                .map(action -> Arguments.of(action, true, memberActions.contains(action), guestActions.contains(action)));
    }
}
