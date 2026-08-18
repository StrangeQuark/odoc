package com.strangequark.odoc.workspace;

import com.strangequark.odoc.encryption.DataEncryptionKey;
import com.strangequark.odoc.encryption.DataEncryptionKeyProvider;
import com.strangequark.odoc.encryption.EncryptionContext;
import com.strangequark.odoc.encryption.EncryptionPurpose;
import com.strangequark.odoc.encryption.ManagedRecordEncryption;
import com.strangequark.odoc.encryption.SecurityScope;
import com.strangequark.odoc.encryption.SecurityScopeKind;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Transactional workspace invariants shared by enrollment today and the authorization/API layers
 * added in later packages. A workspace row lock serializes owner and group membership changes.
 */
@Service
public class WorkspaceDomainService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceGroupRepository groups;
    private final WorkspaceGroupMemberRepository groupMembers;
    private final DataEncryptionKeyProvider keys;
    private final ManagedRecordEncryption encryption;
    private final Clock clock;

    @Autowired
    WorkspaceDomainService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceGroupRepository groups,
            WorkspaceGroupMemberRepository groupMembers,
            DataEncryptionKeyProvider keys,
            ManagedRecordEncryption encryption) {
        this(workspaces, memberships, groups, groupMembers, keys, encryption, Clock.systemUTC());
    }

    WorkspaceDomainService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceGroupRepository groups,
            WorkspaceGroupMemberRepository groupMembers,
            DataEncryptionKeyProvider keys,
            ManagedRecordEncryption encryption,
            Clock clock) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.keys = keys;
        this.encryption = encryption;
        this.clock = clock;
    }

    /** Creates a private tenant scope and its initial purpose-separated keys with its owner. */
    @Transactional
    public UUID createOwnedWorkspace(UUID actorId, String displayName) {
        if (actorId == null) throw new IllegalArgumentException("actorId is required");
        Instant now = clock.instant();
        UUID workspaceId = UUID.randomUUID();
        UUID securityScopeId = UUID.randomUUID();
        SecurityScope scope = workspaceScope(securityScopeId);
        // Create the scope's initial keys in the same transaction as the tenant row; a future
        // key-provider failure rolls back both and cannot leave a usable cross-tenant binding.
        keys.activeKey(scope, EncryptionPurpose.WORKSPACE_METADATA);
        keys.activeKey(scope, EncryptionPurpose.WORKSPACE_LOOKUP);
        workspaces.save(new Workspace(workspaceId, validatedDisplayName(displayName), securityScopeId, now));
        memberships.save(new WorkspaceMembership(
                UUID.randomUUID(), workspaceId, actorId, WorkspaceRole.OWNER, now));
        return workspaceId;
    }

    @Transactional
    WorkspaceMembership addMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
        lockActiveWorkspace(workspaceId);
        if (memberships.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent()) {
            throw conflict("That account is already a workspace member.");
        }
        try {
            return memberships.saveAndFlush(new WorkspaceMembership(
                    UUID.randomUUID(), workspaceId, userId, role, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("That account is already a workspace member.", exception);
        }
    }

    @Transactional
    void removeMember(UUID workspaceId, UUID memberId) {
        List<WorkspaceMembership> lockedMembers = lockMembers(workspaceId);
        WorkspaceMembership member = lockedMembers.stream()
                .filter(candidate -> candidate.id().equals(memberId))
                .findFirst()
                .orElseThrow(() -> notFound("Workspace member not found."));
        requireNotLastActiveOwner(lockedMembers, member, "remove");
        memberships.delete(member);
    }

    @Transactional
    void changeMemberRole(UUID workspaceId, UUID memberId, WorkspaceRole newRole) {
        List<WorkspaceMembership> lockedMembers = lockMembers(workspaceId);
        WorkspaceMembership member = lockedMembers.stream()
                .filter(candidate -> candidate.id().equals(memberId))
                .findFirst()
                .orElseThrow(() -> notFound("Workspace member not found."));
        if (member.role() == WorkspaceRole.OWNER && newRole != WorkspaceRole.OWNER) {
            requireNotLastActiveOwner(lockedMembers, member, "demote");
        }
        member.changeRole(newRole);
    }

    @Transactional
    void suspendMember(UUID workspaceId, UUID memberId) {
        List<WorkspaceMembership> lockedMembers = lockMembers(workspaceId);
        WorkspaceMembership member = lockedMembers.stream()
                .filter(candidate -> candidate.id().equals(memberId))
                .findFirst()
                .orElseThrow(() -> notFound("Workspace member not found."));
        if (member.active()) requireNotLastActiveOwner(lockedMembers, member, "suspend");
        member.suspend();
    }

    @Transactional
    void restoreMember(UUID workspaceId, UUID memberId) {
        WorkspaceMembership member = lockMembers(workspaceId).stream()
                .filter(candidate -> candidate.id().equals(memberId))
                .findFirst()
                .orElseThrow(() -> notFound("Workspace member not found."));
        member.restore();
    }

    /** Promotes the successor first and atomically demotes the current owner under one lock. */
    @Transactional
    void transferOwnership(UUID workspaceId, UUID currentOwnerUserId, UUID successorUserId) {
        List<WorkspaceMembership> lockedMembers = lockMembers(workspaceId);
        WorkspaceMembership current = membershipFor(lockedMembers, currentOwnerUserId);
        WorkspaceMembership successor = membershipFor(lockedMembers, successorUserId);
        if (!current.active() || current.role() != WorkspaceRole.OWNER) {
            throw forbidden("Only an active workspace owner can transfer ownership.");
        }
        if (!successor.active()) throw conflict("The successor must be an active workspace member.");
        successor.changeRole(WorkspaceRole.OWNER);
        if (!current.id().equals(successor.id())) current.changeRole(WorkspaceRole.MEMBER);
    }

    @Transactional
    WorkspaceGroup createGroup(UUID workspaceId, String displayName) {
        Workspace workspace = lockActiveWorkspace(workspaceId);
        UUID groupId = UUID.randomUUID();
        SecurityScope scope = workspaceScope(workspace.securityScopeId());
        String name = validatedDisplayName(displayName);
        byte[] lookup = lookupToken(scope, normalizedName(name));
        String envelope = WorkspaceEncryptedRecordCodec.encode(encryption.encrypt(
                new EncryptionContext(scope, groupId, EncryptionPurpose.WORKSPACE_METADATA, 1),
                name.getBytes(StandardCharsets.UTF_8)));
        try {
            return groups.saveAndFlush(new WorkspaceGroup(groupId, workspaceId, lookup, envelope, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A group with that name already exists in this workspace.", exception);
        }
    }

    @Transactional
    Workspace renameWorkspace(UUID workspaceId, String displayName) {
        Workspace workspace = lockActiveWorkspace(workspaceId);
        workspace.rename(validatedDisplayName(displayName), clock.instant());
        return workspaces.saveAndFlush(workspace);
    }

    @Transactional
    void addGroupMember(UUID workspaceId, UUID groupId, UUID userId) {
        lockActiveWorkspace(workspaceId);
        WorkspaceGroup group = groups.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> notFound("Workspace group not found."));
        if (!group.active()) throw conflict("Workspace group is suspended.");
        WorkspaceMembership membership = memberships.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> conflict("Group members must belong to the same workspace."));
        if (!membership.active()) throw conflict("Suspended members cannot join a group.");
        try {
            groupMembers.saveAndFlush(new WorkspaceGroupMember(groupId, workspaceId, userId, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("That account is already in this group.", exception);
        }
    }

    @Transactional
    void removeGroupMember(UUID workspaceId, UUID groupId, UUID userId) {
        lockActiveWorkspace(workspaceId);
        groups.findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> notFound("Workspace group not found."));
        WorkspaceGroupMember.Key key = new WorkspaceGroupMember.Key(groupId, userId);
        if (!groupMembers.existsById(key)) throw notFound("Workspace group member not found.");
        groupMembers.deleteById(key);
    }

    String decryptGroupName(Workspace workspace, WorkspaceGroup group) {
        SecurityScope scope = workspaceScope(workspace.securityScopeId());
        byte[] plaintext = encryption.decrypt(
                new EncryptionContext(scope, group.id(), EncryptionPurpose.WORKSPACE_METADATA, 1),
                WorkspaceEncryptedRecordCodec.decode(group.nameEnvelope()));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private Workspace lockActiveWorkspace(UUID workspaceId) {
        Workspace workspace = workspaces.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> notFound("Workspace not found."));
        if (!workspace.active()) throw conflict("Workspace is suspended.");
        return workspace;
    }

    private List<WorkspaceMembership> lockMembers(UUID workspaceId) {
        lockActiveWorkspace(workspaceId);
        return memberships.findAllByWorkspaceIdForUpdate(workspaceId);
    }

    private static WorkspaceMembership membershipFor(List<WorkspaceMembership> memberships, UUID userId) {
        return memberships.stream().filter(member -> member.userId().equals(userId)).findFirst()
                .orElseThrow(() -> notFound("Workspace member not found."));
    }

    private static void requireNotLastActiveOwner(
            List<WorkspaceMembership> memberships, WorkspaceMembership target, String action) {
        if (target.role() != WorkspaceRole.OWNER || !target.active()) return;
        long activeOwners = memberships.stream()
                .filter(member -> member.active() && member.role() == WorkspaceRole.OWNER)
                .count();
        if (activeOwners <= 1) throw conflict("Transfer ownership before you " + action + " the last workspace owner.");
    }

    private byte[] lookupToken(SecurityScope scope, String normalizedName) {
        try {
            DataEncryptionKey key = keys.activeKey(scope, EncryptionPurpose.WORKSPACE_LOOKUP);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.key().getEncoded(), "HmacSHA256"));
            return mac.doFinal(normalizedName.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create workspace group lookup token.", exception);
        }
    }

    private static String validatedDisplayName(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace and group names must be 1 to 160 characters.");
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizedName(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static SecurityScope workspaceScope(UUID scopeId) {
        return new SecurityScope(SecurityScopeKind.WORKSPACE, scopeId);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException conflict(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
