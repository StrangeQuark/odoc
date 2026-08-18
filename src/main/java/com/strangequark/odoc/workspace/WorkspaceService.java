package com.strangequark.odoc.workspace;

import com.strangequark.odoc.auth.LocalAccountSummary;
import com.strangequark.odoc.auth.LocalAuthService;
import com.strangequark.odoc.api.OptimisticConcurrency;
import com.strangequark.odoc.authorization.AuthorizationAction;
import com.strangequark.odoc.audit.AuditPublisher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Membership administration for the local-account MVP. */
@Service
public class WorkspaceService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceInvitationCapabilityRepository invitationCapabilities;
    private final WorkspaceGroupRepository groups;
    private final WorkspaceGroupMemberRepository groupMembers;
    private final WorkspaceAccessService access;
    private final LocalAuthService accounts;
    private final WorkspaceDomainService domain;
    private final AuditPublisher audit;
    private final Clock clock;

    @Autowired
    WorkspaceService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceInvitationRepository invitations,
            WorkspaceInvitationCapabilityRepository invitationCapabilities,
            WorkspaceGroupRepository groups,
            WorkspaceGroupMemberRepository groupMembers,
            WorkspaceAccessService access,
            LocalAuthService accounts,
            WorkspaceDomainService domain,
            AuditPublisher audit) {
        this(workspaces, memberships, invitations, invitationCapabilities, groups, groupMembers,
                access, accounts, domain, audit, Clock.systemUTC());
    }

    /** Narrow constructor retained for the existing domain-focused unit tests. */
    WorkspaceService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceInvitationRepository invitations,
            WorkspaceAccessService access,
            LocalAuthService accounts,
            WorkspaceDomainService domain,
            Clock clock) {
        this(workspaces, memberships, invitations, null, null, null, access, accounts, domain, null, clock);
    }

    WorkspaceService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceInvitationRepository invitations,
            WorkspaceInvitationCapabilityRepository invitationCapabilities,
            WorkspaceGroupRepository groups,
            WorkspaceGroupMemberRepository groupMembers,
            WorkspaceAccessService access,
            LocalAuthService accounts,
            WorkspaceDomainService domain,
            Clock clock) {
        this(workspaces, memberships, invitations, invitationCapabilities, groups, groupMembers,
                access, accounts, domain, null, clock);
    }

    WorkspaceService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceInvitationRepository invitations,
            WorkspaceInvitationCapabilityRepository invitationCapabilities,
            WorkspaceGroupRepository groups,
            WorkspaceGroupMemberRepository groupMembers,
            WorkspaceAccessService access,
            LocalAuthService accounts,
            WorkspaceDomainService domain,
            AuditPublisher audit,
            Clock clock) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.invitations = invitations;
        this.invitationCapabilities = invitationCapabilities;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.access = access;
        this.accounts = accounts;
        this.domain = domain;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<WorkspaceResponse> listForCurrentUser() {
        return access.workspaceIdsForCurrentUser().stream()
                .map(workspaceId -> {
                    WorkspaceMembership membership = access.requireCurrentMembership(workspaceId);
                    return workspaces.findById(workspaceId)
                        .map(workspace -> WorkspaceResponse.from(workspace, membership))
                        .orElse(null);
                })
                .filter(response -> response != null)
                .toList();
    }

    @Transactional(readOnly = true)
    WorkspaceResponse getWorkspace(UUID workspaceId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_VIEW);
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        return WorkspaceResponse.from(workspace, access.requireCurrentMembership(workspaceId));
    }

    @Transactional
    WorkspaceResponse createWorkspace(CreateWorkspaceRequest request) {
        UUID workspaceId = domain.createOwnedWorkspace(access.currentUserId(), request.name());
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new IllegalStateException("New workspace was not persisted."));
        record(workspaceId, "workspace.created", "workspace", workspaceId, "success", "workspace-create-" + workspaceId);
        return new WorkspaceResponse(workspace.id(), workspace.name(), WorkspaceRole.OWNER, workspace.revision());
    }

    @Transactional
    WorkspaceResponse updateWorkspace(UUID workspaceId, String ifMatch, UpdateWorkspaceRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_CONFIGURE);
        Workspace current = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        OptimisticConcurrency.requireMatching(ifMatch, current.revision());
        Workspace updated = domain.renameWorkspace(workspaceId, request.name());
        record(workspaceId, "workspace.updated", "workspace", workspaceId, "success",
                "workspace-update-" + workspaceId + "-" + updated.revision());
        return WorkspaceResponse.from(updated, access.requireCurrentMembership(workspaceId));
    }

    @Transactional(readOnly = true)
    List<WorkspaceMemberResponse> listMembers(UUID workspaceId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_MEMBERS);
        return memberships.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    WorkspaceMemberResponse invite(UUID workspaceId, InviteWorkspaceMemberRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_INVITE);
        LocalAccountSummary account = accounts.findActiveAccount(request.email());
        return toMemberResponse(domain.addMember(workspaceId, account.id(), WorkspaceRole.MEMBER));
    }

    @Transactional
    InvitationDelivery createInvitation(UUID workspaceId, InviteWorkspaceMemberRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_INVITE);
        String workspaceName = workspaces.findById(workspaceId).map(Workspace::name).orElse("an Odoc workspace");
        UUID invitationId = UUID.randomUUID();
        LocalAuthService.EncryptedEmailAddress recipient = accounts.encryptInvitationEmail(invitationId, request.email());
        Instant now = clock.instant();
        invitations.findAllByWorkspaceIdAndEmailLookupTokenAndAcceptedAtIsNullAndRevokedAtIsNull(
                        workspaceId, recipient.lookupToken())
                .forEach(invitation -> invitation.revoke(now));
        String verifier = randomToken();
        WorkspaceInvitation invitation = invitations.saveAndFlush(new WorkspaceInvitation(
                invitationId, workspaceId, recipient.lookupToken(), recipient.envelope(), sha256(verifier),
                UUID.randomUUID(), now.plus(INVITATION_TTL), now));
        record(workspaceId, "workspace.invitation.created", "workspace_invitation", invitation.id(), "success",
                "workspace-invitation-" + invitation.id());
        return new InvitationDelivery(
                toInvitationResponse(invitation), accounts.decryptInvitationEmail(invitationId, recipient.envelope()),
                verifier, workspaceName);
    }

    @Transactional(readOnly = true)
    List<WorkspaceInvitationResponse> listInvitations(UUID workspaceId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_INVITE);
        return invitations.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .filter(invitation -> invitation.usableAt(clock.instant()))
                .map(this::toInvitationResponse)
                .toList();
    }

    @Transactional
    void revokeInvitation(UUID workspaceId, UUID invitationId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_INVITE);
        WorkspaceInvitation invitation = invitations.findById(invitationId)
                .filter(candidate -> candidate.workspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace invitation not found."));
        invitation.revoke(clock.instant());
        record(workspaceId, "workspace.invitation.revoked", "workspace_invitation", invitationId, "success",
                "workspace-invitation-revoke-" + invitationId);
    }

    @Transactional
    void acceptInvitation(String verifier) {
        if (verifier == null || verifier.isBlank() || verifier.length() > 256) throw invalidInvitation();
        Instant now = clock.instant();
        WorkspaceInvitation invitation = invitations.findByTokenHashForUpdate(sha256(verifier))
                .orElseThrow(WorkspaceService::invalidInvitation);
        if (!accounts.invitationIsFor(invitation.id(), invitation.emailEnvelope(), access.currentUserEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation was sent to a different email address.");
        }
        UUID userId = access.currentUserId();
        if (invitation.recipientCanRetryAt(now)) return;
        if (!invitation.usableAt(now)) throw invalidInvitation();
        domain.addMember(invitation.workspaceId(), userId, WorkspaceRole.MEMBER);
        invitation.accept(now);
        record(invitation.workspaceId(), "workspace.invitation.accepted", "workspace_invitation", invitation.id(), "success",
                "workspace-invitation-accept-" + invitation.id());
    }

    @Transactional
    InvitationCapability exchangeInvitation(UUID routeId, String verifier) {
        if (verifier == null || verifier.isBlank() || verifier.length() > 256) throw invalidInvitation();
        WorkspaceInvitation invitation = invitations.findByRouteIdForUpdate(routeId)
                .orElseThrow(WorkspaceService::invalidInvitation);
        if (!invitation.usableAt(clock.instant())
                || !MessageDigest.isEqual(invitation.tokenHash(), sha256(verifier))) {
            throw invalidInvitation();
        }
        String token = randomToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(15));
        capabilities().saveAndFlush(new WorkspaceInvitationCapability(
                UUID.randomUUID(), invitation.id(), sha256(token), expiresAt, now));
        return new InvitationCapability(token, expiresAt);
    }

    @Transactional
    void acceptInvitationCapability(String capabilityToken) {
        if (capabilityToken == null || capabilityToken.isBlank() || capabilityToken.length() > 256) throw invalidInvitation();
        Instant now = clock.instant();
        WorkspaceInvitationCapability capability = capabilities().findByTokenHashForUpdate(sha256(capabilityToken))
                .filter(candidate -> candidate.usableAt(now))
                .orElseThrow(WorkspaceService::invalidInvitation);
        WorkspaceInvitation invitation = invitations.findById(capability.invitationId())
                .orElseThrow(WorkspaceService::invalidInvitation);
        if (!accounts.invitationIsFor(invitation.id(), invitation.emailEnvelope(), access.currentUserEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation was sent to a different email address.");
        }
        if (invitation.recipientCanRetryAt(now)) return;
        if (!invitation.usableAt(now)) throw invalidInvitation();
        domain.addMember(invitation.workspaceId(), access.currentUserId(), WorkspaceRole.MEMBER);
        invitation.accept(now);
        record(invitation.workspaceId(), "workspace.invitation.accepted", "workspace_invitation", invitation.id(), "success",
                "workspace-invitation-accept-" + invitation.id());
    }

    /**
     * Atomically creates an account for an invited email and consumes the invitation. This is the
     * only local-account enrollment path allowed when {@code ODOC_AUTH_INVITE_ONLY=true}.
     */
    @Transactional
    public UUID registerInvitedAccount(String email, String password, String verifier) {
        WorkspaceInvitation invitation = usableInvitation(verifier);
        if (!accounts.invitationIsFor(invitation.id(), invitation.emailEnvelope(), email)) {
            throw invalidInvitation();
        }
        UUID userId = accounts.registerFromInvitation(email, password);
        invitation.accept(clock.instant());
        domain.addMember(invitation.workspaceId(), userId, WorkspaceRole.MEMBER);
        if (audit != null) {
            audit.record(invitation.workspaceId(), userId, "workspace.invitation.accepted", "workspace_invitation",
                    invitation.id(), "success", "workspace-invitation-accept-" + invitation.id());
        }
        return userId;
    }

    @Transactional
    void removeMember(UUID workspaceId, UUID memberId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_MEMBERS);
        domain.removeMember(workspaceId, memberId);
        record(workspaceId, "workspace.member.removed", "workspace_member", memberId, "success",
                "workspace-member-remove-" + workspaceId + "-" + memberId);
    }

    @Transactional
    WorkspaceMemberResponse changeMemberRole(
            UUID workspaceId, UUID memberId, UpdateWorkspaceMemberRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_MEMBERS);
        if (request.role() == WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use ownership transfer to assign the owner role.");
        }
        domain.changeMemberRole(workspaceId, memberId, request.role());
        record(workspaceId, "workspace.member.role.updated", "workspace_member", memberId, "success",
                "workspace-member-role-" + workspaceId + "-" + memberId + "-" + request.role());
        return memberships.findById(memberId).map(this::toMemberResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace member not found."));
    }

    @Transactional
    void transferOwnership(UUID workspaceId, TransferWorkspaceOwnershipRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_MEMBERS);
        domain.transferOwnership(workspaceId, access.currentUserId(), request.successorUserId());
        record(workspaceId, "workspace.ownership.transferred", "workspace_member", request.successorUserId(), "success",
                "workspace-ownership-transfer-" + workspaceId + "-" + request.successorUserId());
    }

    @Transactional(readOnly = true)
    List<WorkspaceGroupResponse> listGroups(UUID workspaceId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_GROUPS);
        Workspace workspace = requiredWorkspace(workspaceId);
        return groups().findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(group -> toGroupResponse(workspace, group)).toList();
    }

    @Transactional
    WorkspaceGroupResponse createGroup(UUID workspaceId, CreateWorkspaceGroupRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_GROUPS);
        WorkspaceGroup group = domain.createGroup(workspaceId, request.name());
        record(workspaceId, "workspace.group.created", "workspace_group", group.id(), "success",
                "workspace-group-create-" + group.id());
        return toGroupResponse(requiredWorkspace(workspaceId), group);
    }

    @Transactional(readOnly = true)
    List<WorkspaceGroupMemberResponse> listGroupMembers(UUID workspaceId, UUID groupId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_GROUPS);
        groups().findByIdAndWorkspaceId(groupId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace group not found."));
        return groupMembers().findAllByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(member -> new WorkspaceGroupMemberResponse(
                        member.userId(), accounts.emailFor(member.userId()), member.createdAt()))
                .toList();
    }

    @Transactional
    void addGroupMember(UUID workspaceId, UUID groupId, AddWorkspaceGroupMemberRequest request) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_GROUPS);
        domain.addGroupMember(workspaceId, groupId, request.userId());
        record(workspaceId, "workspace.group.member.added", "workspace_group", groupId, "success",
                "workspace-group-member-add-" + groupId + "-" + request.userId());
    }

    @Transactional
    void removeGroupMember(UUID workspaceId, UUID groupId, UUID userId) {
        access.requireWorkspaceAction(workspaceId, AuthorizationAction.WORKSPACE_MANAGE_GROUPS);
        domain.removeGroupMember(workspaceId, groupId, userId);
        record(workspaceId, "workspace.group.member.removed", "workspace_group", groupId, "success",
                "workspace-group-member-remove-" + groupId + "-" + userId);
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMembership membership) {
        return new WorkspaceMemberResponse(
                membership.id(),
                membership.userId(),
                accounts.emailFor(membership.userId()),
                membership.role(),
                membership.createdAt());
    }

    private WorkspaceInvitationResponse toInvitationResponse(WorkspaceInvitation invitation) {
        return new WorkspaceInvitationResponse(
                invitation.id(), invitation.routeId(), accounts.decryptInvitationEmail(invitation.id(), invitation.emailEnvelope()),
                invitation.expiresAt(), invitation.createdAt());
    }

    private WorkspaceGroupResponse toGroupResponse(Workspace workspace, WorkspaceGroup group) {
        return new WorkspaceGroupResponse(
                group.id(), domain.decryptGroupName(workspace, group), group.status(), group.revision(), group.createdAt());
    }

    private Workspace requiredWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
    }

    private WorkspaceInvitationCapabilityRepository capabilities() {
        if (invitationCapabilities == null) throw new IllegalStateException("Invitation capabilities are unavailable.");
        return invitationCapabilities;
    }

    private WorkspaceGroupRepository groups() {
        if (groups == null) throw new IllegalStateException("Workspace groups are unavailable.");
        return groups;
    }

    private WorkspaceGroupMemberRepository groupMembers() {
        if (groupMembers == null) throw new IllegalStateException("Workspace group members are unavailable.");
        return groupMembers;
    }

    private void record(UUID workspaceId, String action, String targetType, UUID targetId, String outcome,
            String idempotencyKey) {
        if (audit != null) audit.record(workspaceId, access.currentUserId(), action, targetType, targetId, outcome, idempotencyKey);
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This invitation is invalid or expired.");
    }

    private WorkspaceInvitation usableInvitation(String verifier) {
        if (verifier == null || verifier.isBlank() || verifier.length() > 256) throw invalidInvitation();
        return invitations.findByTokenHashForUpdate(sha256(verifier))
                .filter(candidate -> candidate.usableAt(clock.instant()))
                .orElseThrow(WorkspaceService::invalidInvitation);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    record InvitationDelivery(WorkspaceInvitationResponse invitation, String recipient, String verifier, String workspaceName) {}
    record InvitationCapability(String token, Instant expiresAt) {}
}
