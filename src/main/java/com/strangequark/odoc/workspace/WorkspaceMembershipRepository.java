package com.strangequark.odoc.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    List<WorkspaceMembership> findAllByUserIdOrderByCreatedAtAsc(UUID userId);
    List<WorkspaceMembership> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from WorkspaceMembership membership where membership.workspaceId = :workspaceId order by membership.createdAt asc")
    List<WorkspaceMembership> findAllByWorkspaceIdForUpdate(UUID workspaceId);
}
