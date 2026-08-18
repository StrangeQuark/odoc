package com.strangequark.odoc.workspace;

import com.strangequark.odoc.auth.LocalAccountSummary;
import com.strangequark.odoc.auth.LocalAuthService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Membership administration for the local-account MVP. */
@Service
class WorkspaceService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceAccessService access;
    private final LocalAuthService accounts;
    private final Clock clock;

    @Autowired
    WorkspaceService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceInvitationRepository invitations,
            WorkspaceAccessService access,
            LocalAuthService accounts) {
        this(workspaces, memberships, invitations, access, accounts, Clock.systemUTC());
    }

    WorkspaceService(
            WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships,
            WorkspaceInvitationRepository invitations,
            WorkspaceAccessService access,
            LocalAuthService accounts,
            Clock clock) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.invitations = invitations;
        this.access = access;
        this.accounts = accounts;
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
    List<WorkspaceMemberResponse> listMembers(UUID workspaceId) {
        access.requireCurrentOwner(workspaceId);
        return memberships.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    WorkspaceMemberResponse invite(UUID workspaceId, InviteWorkspaceMemberRequest request) {
        access.requireCurrentOwner(workspaceId);
        LocalAccountSummary account = accounts.findActiveAccount(request.email());
        if (memberships.existsByWorkspaceIdAndUserId(workspaceId, account.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That account is already a workspace member.");
        }
        WorkspaceMembership membership = new WorkspaceMembership(
                UUID.randomUUID(), workspaceId, account.id(), WorkspaceRole.MEMBER, clock.instant());
        try {
            return toMemberResponse(memberships.saveAndFlush(membership));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That account is already a workspace member.", exception);
        }
    }

    @Transactional
    InvitationDelivery createInvitation(UUID workspaceId, InviteWorkspaceMemberRequest request) {
        access.requireCurrentOwner(workspaceId);
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
                now.plus(INVITATION_TTL), now));
        return new InvitationDelivery(
                toInvitationResponse(invitation), accounts.decryptInvitationEmail(invitationId, recipient.envelope()),
                verifier, workspaceName);
    }

    @Transactional(readOnly = true)
    List<WorkspaceInvitationResponse> listInvitations(UUID workspaceId) {
        access.requireCurrentOwner(workspaceId);
        return invitations.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .filter(invitation -> invitation.usableAt(clock.instant()))
                .map(this::toInvitationResponse)
                .toList();
    }

    @Transactional
    void revokeInvitation(UUID workspaceId, UUID invitationId) {
        access.requireCurrentOwner(workspaceId);
        WorkspaceInvitation invitation = invitations.findById(invitationId)
                .filter(candidate -> candidate.workspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace invitation not found."));
        invitation.revoke(clock.instant());
    }

    @Transactional
    void acceptInvitation(String verifier) {
        if (verifier == null || verifier.isBlank() || verifier.length() > 256) throw invalidInvitation();
        Instant now = clock.instant();
        WorkspaceInvitation invitation = invitations.findByTokenHash(sha256(verifier))
                .filter(candidate -> candidate.usableAt(now))
                .orElseThrow(WorkspaceService::invalidInvitation);
        if (!accounts.invitationIsFor(invitation.id(), invitation.emailEnvelope(), access.currentUserEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation was sent to a different email address.");
        }
        invitation.accept(now);
        UUID userId = access.currentUserId();
        if (!memberships.existsByWorkspaceIdAndUserId(invitation.workspaceId(), userId)) {
            memberships.save(new WorkspaceMembership(
                    UUID.randomUUID(), invitation.workspaceId(), userId, WorkspaceRole.MEMBER, now));
        }
    }

    @Transactional
    void removeMember(UUID workspaceId, UUID memberId) {
        access.requireCurrentOwner(workspaceId);
        WorkspaceMembership member = memberships.findById(memberId)
                .filter(candidate -> candidate.workspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace member not found."));
        if (member.role() == WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A workspace owner cannot be removed.");
        }
        memberships.delete(member);
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
                invitation.id(), accounts.decryptInvitationEmail(invitation.id(), invitation.emailEnvelope()),
                invitation.expiresAt(), invitation.createdAt());
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This invitation is invalid or expired.");
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
}
