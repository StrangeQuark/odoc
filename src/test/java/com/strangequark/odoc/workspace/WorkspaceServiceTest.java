package com.strangequark.odoc.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strangequark.odoc.auth.LocalAuthService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock private WorkspaceRepository workspaces;
    @Mock private WorkspaceMembershipRepository memberships;
    @Mock private WorkspaceInvitationRepository invitations;
    @Mock private WorkspaceAccessService access;
    @Mock private LocalAuthService accounts;

    @Test
    void letsANewMatchingAccountAcceptAnEmailedInvitationExactlyOnce() throws Exception {
        Instant now = Instant.parse("2026-08-17T17:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        UUID workspaceId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        String recipientEmail = "new.member@example.test";
        byte[] lookupToken = "email-lookup".getBytes(StandardCharsets.UTF_8);
        WorkspaceService service = new WorkspaceService(workspaces, memberships, invitations, access, accounts, clock);

        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(new Workspace(workspaceId, "Engineering", now)));
        when(accounts.encryptInvitationEmail(any(UUID.class), eq(recipientEmail)))
                .thenReturn(new LocalAuthService.EncryptedEmailAddress(lookupToken, "sealed-recipient"));
        when(accounts.decryptInvitationEmail(any(UUID.class), eq("sealed-recipient"))).thenReturn(recipientEmail);
        when(invitations.findAllByWorkspaceIdAndEmailLookupTokenAndAcceptedAtIsNullAndRevokedAtIsNull(
                        eq(workspaceId), argThat(token -> Arrays.equals(lookupToken, token))))
                .thenReturn(java.util.List.of());
        when(invitations.saveAndFlush(any(WorkspaceInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceService.InvitationDelivery delivery = service.createInvitation(
                workspaceId, new InviteWorkspaceMemberRequest(recipientEmail));

        assertThat(delivery.recipient()).isEqualTo(recipientEmail);
        assertThat(delivery.workspaceName()).isEqualTo("Engineering");
        assertThat(delivery.verifier()).matches("^[A-Za-z0-9_-]{40,}$");
        assertThat(delivery.invitation().expiresAt()).isEqualTo(now.plusSeconds(7 * 24 * 60 * 60));

        ArgumentCaptor<WorkspaceInvitation> invitationCaptor = ArgumentCaptor.forClass(WorkspaceInvitation.class);
        verify(invitations).saveAndFlush(invitationCaptor.capture());
        WorkspaceInvitation storedInvitation = invitationCaptor.getValue();
        when(invitations.findByTokenHash(argThat(hash -> Arrays.equals(hash, sha256(delivery.verifier())))))
                .thenReturn(Optional.of(storedInvitation));
        when(access.currentUserEmail()).thenReturn(recipientEmail);
        when(access.currentUserId()).thenReturn(recipientId);
        when(accounts.invitationIsFor(storedInvitation.id(), storedInvitation.emailEnvelope(), recipientEmail))
                .thenReturn(true);
        when(memberships.existsByWorkspaceIdAndUserId(workspaceId, recipientId)).thenReturn(false);

        service.acceptInvitation(delivery.verifier());

        ArgumentCaptor<WorkspaceMembership> membershipCaptor = ArgumentCaptor.forClass(WorkspaceMembership.class);
        verify(memberships).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().workspaceId()).isEqualTo(workspaceId);
        assertThat(membershipCaptor.getValue().userId()).isEqualTo(recipientId);
        assertThat(membershipCaptor.getValue().role()).isEqualTo(WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> service.acceptInvitation(delivery.verifier()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void refusesAnInvitationWhenTheSignedInEmailDoesNotMatchTheRecipient() throws Exception {
        Instant now = Instant.parse("2026-08-17T17:00:00Z");
        UUID invitationId = UUID.randomUUID();
        String verifier = "recipient-specific-verifier";
        WorkspaceInvitation invitation = new WorkspaceInvitation(
                invitationId, UUID.randomUUID(), new byte[] {1}, "sealed-recipient", sha256(verifier),
                now.plusSeconds(60), now);
        WorkspaceService service = new WorkspaceService(workspaces, memberships, invitations, access, accounts,
                Clock.fixed(now, ZoneOffset.UTC));
        when(invitations.findByTokenHash(argThat(hash -> Arrays.equals(hash, sha256(verifier)))))
                .thenReturn(Optional.of(invitation));
        when(access.currentUserEmail()).thenReturn("different.person@example.test");
        when(accounts.invitationIsFor(invitationId, "sealed-recipient", "different.person@example.test"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.acceptInvitation(verifier))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(invitation.usableAt(now)).isTrue();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
