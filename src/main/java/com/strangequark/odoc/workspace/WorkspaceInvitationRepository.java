package com.strangequark.odoc.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {
    Optional<WorkspaceInvitation> findByTokenHash(byte[] tokenHash);
    List<WorkspaceInvitation> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    List<WorkspaceInvitation> findAllByWorkspaceIdAndEmailLookupTokenAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID workspaceId, byte[] emailLookupToken);
}
