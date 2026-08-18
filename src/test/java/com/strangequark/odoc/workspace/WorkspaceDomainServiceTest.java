package com.strangequark.odoc.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.strangequark.odoc.encryption.DataEncryptionKey;
import com.strangequark.odoc.encryption.DataEncryptionKeyProvider;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkspaceDomainServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T18:00:00Z");

    @Mock private WorkspaceRepository workspaces;
    @Mock private WorkspaceMembershipRepository memberships;
    @Mock private WorkspaceGroupRepository groups;
    @Mock private WorkspaceGroupMemberRepository groupMembers;
    @Mock private DataEncryptionKeyProvider keys;
    private WorkspaceDomainService service;

    @BeforeEach
    void setUp() {
        DataEncryptionKey key = new DataEncryptionKey(1, new SecretKeySpec(new byte[32], "AES"));
        lenient().when(keys.activeKey(any(), any())).thenReturn(key);
        service = new WorkspaceDomainService(
                workspaces, memberships, groups, groupMembers, keys, new ManagedRecordEncryption(keys),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAnOpaqueTenantScopeAndOwnerInOneTransaction() {
        UUID actorId = UUID.randomUUID();

        UUID workspaceId = service.createOwnedWorkspace(actorId, " Engineering ");

        ArgumentCaptor<Workspace> workspace = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaces).save(workspace.capture());
        assertThat(workspace.getValue().id()).isEqualTo(workspaceId);
        assertThat(workspace.getValue().securityScopeId()).isNotEqualTo(workspaceId);
        assertThat(workspace.getValue().name()).isEqualTo("Engineering");
        verify(keys).activeKey(eq(new com.strangequark.odoc.encryption.SecurityScope(
                com.strangequark.odoc.encryption.SecurityScopeKind.WORKSPACE,
                workspace.getValue().securityScopeId())), eq(EncryptionPurpose.WORKSPACE_METADATA));
        verify(memberships).save(any(WorkspaceMembership.class));
    }

    @Test
    void refusesToRemoveOrDemoteTheLastActiveOwner() {
        Workspace workspace = activeWorkspace();
        WorkspaceMembership owner = membership(workspace.id(), WorkspaceRole.OWNER, WorkspaceMembershipStatus.ACTIVE);
        when(workspaces.findByIdForUpdate(workspace.id())).thenReturn(Optional.of(workspace));
        when(memberships.findAllByWorkspaceIdForUpdate(workspace.id())).thenReturn(List.of(owner));

        assertConflict(() -> service.removeMember(workspace.id(), owner.id()));
        assertConflict(() -> service.changeMemberRole(workspace.id(), owner.id(), WorkspaceRole.MEMBER));
        assertConflict(() -> service.suspendMember(workspace.id(), owner.id()));
        verify(memberships, never()).delete(any());
    }

    @Test
    void atomicallyTransfersOwnershipBeforeDemotion() {
        Workspace workspace = activeWorkspace();
        UUID currentId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();
        WorkspaceMembership owner = new WorkspaceMembership(UUID.randomUUID(), workspace.id(), currentId, WorkspaceRole.OWNER, NOW);
        WorkspaceMembership successor = new WorkspaceMembership(UUID.randomUUID(), workspace.id(), successorId, WorkspaceRole.MEMBER, NOW);
        when(workspaces.findByIdForUpdate(workspace.id())).thenReturn(Optional.of(workspace));
        when(memberships.findAllByWorkspaceIdForUpdate(workspace.id())).thenReturn(List.of(owner, successor));

        service.transferOwnership(workspace.id(), currentId, successorId);

        assertThat(owner.role()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(successor.role()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void rejectsCrossWorkspaceGroupMembershipBeforePersistingIt() {
        Workspace workspace = activeWorkspace();
        WorkspaceGroup group = new WorkspaceGroup(UUID.randomUUID(), workspace.id(), new byte[] {1}, "envelope", NOW);
        UUID unrelatedUser = UUID.randomUUID();
        when(workspaces.findByIdForUpdate(workspace.id())).thenReturn(Optional.of(workspace));
        when(groups.findByIdAndWorkspaceId(group.id(), workspace.id())).thenReturn(Optional.of(group));
        when(memberships.findByWorkspaceIdAndUserId(workspace.id(), unrelatedUser)).thenReturn(Optional.empty());

        assertConflict(() -> service.addGroupMember(workspace.id(), group.id(), unrelatedUser));
        verify(groupMembers, never()).saveAndFlush(any());
    }

    @Test
    void encryptsGroupNamesAndUsesScopedLookupTokens() {
        Workspace workspace = activeWorkspace();
        when(workspaces.findByIdForUpdate(workspace.id())).thenReturn(Optional.of(workspace));
        when(groups.saveAndFlush(any(WorkspaceGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceGroup group = service.createGroup(workspace.id(), "Platform Team");

        assertThat(group.nameEnvelope()).doesNotContain("Platform Team");
        verify(keys).activeKey(eq(new com.strangequark.odoc.encryption.SecurityScope(
                com.strangequark.odoc.encryption.SecurityScopeKind.WORKSPACE, workspace.securityScopeId())),
                eq(EncryptionPurpose.WORKSPACE_LOOKUP));
    }

    private static Workspace activeWorkspace() {
        return new Workspace(UUID.randomUUID(), "Engineering", UUID.randomUUID(), NOW);
    }

    private static WorkspaceMembership membership(
            UUID workspaceId, WorkspaceRole role, WorkspaceMembershipStatus status) {
        WorkspaceMembership membership = new WorkspaceMembership(UUID.randomUUID(), workspaceId, UUID.randomUUID(), role, NOW);
        if (status == WorkspaceMembershipStatus.SUSPENDED) membership.suspend();
        return membership;
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable operation) {
        try {
            operation.execute();
            fail("Expected workspace invariant to reject the operation.");
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } catch (Throwable exception) {
            throw new AssertionError("Expected a workspace conflict.", exception);
        }
    }
}
