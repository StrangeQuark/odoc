package com.strangequark.odoc.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {
    Optional<WorkspaceInvitation> findByTokenHash(byte[] tokenHash);
    Optional<WorkspaceInvitation> findByRouteId(UUID routeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from WorkspaceInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<WorkspaceInvitation> findByTokenHashForUpdate(byte[] tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from WorkspaceInvitation invitation where invitation.routeId = :routeId")
    Optional<WorkspaceInvitation> findByRouteIdForUpdate(UUID routeId);
    List<WorkspaceInvitation> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    List<WorkspaceInvitation> findAllByWorkspaceIdAndEmailLookupTokenAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID workspaceId, byte[] emailLookupToken);
}
