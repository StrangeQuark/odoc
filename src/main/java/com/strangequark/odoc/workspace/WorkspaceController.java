package com.strangequark.odoc.workspace;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.strangequark.odoc.auth.AccountRecoveryMailService;
import org.springframework.mail.MailException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaces;
    private final AccountRecoveryMailService mail;

    WorkspaceController(WorkspaceService workspaces, AccountRecoveryMailService mail) {
        this.workspaces = workspaces;
        this.mail = mail;
    }

    @GetMapping
    List<WorkspaceResponse> list() {
        return workspaces.listForCurrentUser();
    }

    @GetMapping("/{workspaceId}/members")
    List<WorkspaceMemberResponse> members(@PathVariable UUID workspaceId) {
        return workspaces.listMembers(workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    ResponseEntity<WorkspaceMemberResponse> invite(
            @PathVariable UUID workspaceId, @Valid @RequestBody InviteWorkspaceMemberRequest request) {
        WorkspaceMemberResponse member = workspaces.invite(workspaceId, request);
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspaceId + "/members/" + member.id()))
                .body(member);
    }

    @GetMapping("/{workspaceId}/invitations")
    List<WorkspaceInvitationResponse> invitations(@PathVariable UUID workspaceId) {
        return workspaces.listInvitations(workspaceId);
    }

    @PostMapping("/{workspaceId}/invitations")
    ResponseEntity<WorkspaceInvitationResponse> createInvitation(
            @PathVariable UUID workspaceId, @Valid @RequestBody InviteWorkspaceMemberRequest request) {
        WorkspaceService.InvitationDelivery delivery = workspaces.createInvitation(workspaceId, request);
        try {
            mail.sendWorkspaceInvitation(delivery.recipient(), delivery.workspaceName(), delivery.verifier());
        } catch (MailException ignored) {
            // Local Mailpit is a delivery adapter; the invite itself is durable and can be reissued.
        }
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspaceId + "/invitations/" + delivery.invitation().id()))
                .body(delivery.invitation());
    }

    @DeleteMapping("/{workspaceId}/invitations/{invitationId}")
    ResponseEntity<Void> revokeInvitation(@PathVariable UUID workspaceId, @PathVariable UUID invitationId) {
        workspaces.revokeInvitation(workspaceId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/accept")
    ResponseEntity<Void> acceptInvitation(@Valid @RequestBody AcceptWorkspaceInvitationRequest request) {
        workspaces.acceptInvitation(request.verifier());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    ResponseEntity<Void> remove(@PathVariable UUID workspaceId, @PathVariable UUID memberId) {
        workspaces.removeMember(workspaceId, memberId);
        return ResponseEntity.noContent().build();
    }
}
