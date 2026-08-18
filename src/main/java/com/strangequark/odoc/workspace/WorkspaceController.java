package com.strangequark.odoc.workspace;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.strangequark.odoc.api.OptimisticConcurrency;
import com.strangequark.odoc.auth.AccountRecoveryMailService;
import org.springframework.mail.MailException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PostMapping
    ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse workspace = workspaces.createWorkspace(request);
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspace.id()))
                .eTag(OptimisticConcurrency.etag(workspace.revision())).body(workspace);
    }

    @GetMapping("/{workspaceId}")
    ResponseEntity<WorkspaceResponse> get(@PathVariable UUID workspaceId) {
        WorkspaceResponse workspace = workspaces.getWorkspace(workspaceId);
        return ResponseEntity.ok().eTag(OptimisticConcurrency.etag(workspace.revision())).body(workspace);
    }

    @PatchMapping("/{workspaceId}")
    ResponseEntity<WorkspaceResponse> update(
            @PathVariable UUID workspaceId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        WorkspaceResponse workspace = workspaces.updateWorkspace(workspaceId, ifMatch, request);
        return ResponseEntity.ok().eTag(OptimisticConcurrency.etag(workspace.revision())).body(workspace);
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
            mail.sendWorkspaceInvitation(
                    delivery.recipient(), delivery.workspaceName(), delivery.invitation().routeId(), delivery.verifier());
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

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    ResponseEntity<Void> remove(@PathVariable UUID workspaceId, @PathVariable UUID memberId) {
        workspaces.removeMember(workspaceId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{workspaceId}/members/{memberId}")
    WorkspaceMemberResponse updateMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateWorkspaceMemberRequest request) {
        return workspaces.changeMemberRole(workspaceId, memberId, request);
    }

    @PostMapping("/{workspaceId}/ownership-transfer")
    ResponseEntity<Void> transferOwnership(
            @PathVariable UUID workspaceId, @Valid @RequestBody TransferWorkspaceOwnershipRequest request) {
        workspaces.transferOwnership(workspaceId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/groups")
    List<WorkspaceGroupResponse> groups(@PathVariable UUID workspaceId) {
        return workspaces.listGroups(workspaceId);
    }

    @PostMapping("/{workspaceId}/groups")
    ResponseEntity<WorkspaceGroupResponse> createGroup(
            @PathVariable UUID workspaceId, @Valid @RequestBody CreateWorkspaceGroupRequest request) {
        WorkspaceGroupResponse group = workspaces.createGroup(workspaceId, request);
        return ResponseEntity.created(URI.create("/api/v1/workspaces/" + workspaceId + "/groups/" + group.id()))
                .eTag(OptimisticConcurrency.etag(group.revision())).body(group);
    }

    @GetMapping("/{workspaceId}/groups/{groupId}/members")
    List<WorkspaceGroupMemberResponse> groupMembers(@PathVariable UUID workspaceId, @PathVariable UUID groupId) {
        return workspaces.listGroupMembers(workspaceId, groupId);
    }

    @PostMapping("/{workspaceId}/groups/{groupId}/members")
    ResponseEntity<Void> addGroupMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID groupId,
            @Valid @RequestBody AddWorkspaceGroupMemberRequest request) {
        workspaces.addGroupMember(workspaceId, groupId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{workspaceId}/groups/{groupId}/members/{userId}")
    ResponseEntity<Void> removeGroupMember(
            @PathVariable UUID workspaceId, @PathVariable UUID groupId, @PathVariable UUID userId) {
        workspaces.removeGroupMember(workspaceId, groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
