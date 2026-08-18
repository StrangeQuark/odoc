package com.strangequark.odoc.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    List<WorkspaceMembership> findAllByUserIdOrderByCreatedAtAsc(UUID userId);
    List<WorkspaceMembership> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
