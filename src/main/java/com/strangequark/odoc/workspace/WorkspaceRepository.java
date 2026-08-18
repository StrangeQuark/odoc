package com.strangequark.odoc.workspace;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workspace from Workspace workspace where workspace.id = :workspaceId")
    Optional<Workspace> findByIdForUpdate(UUID workspaceId);

    @Query("select workspace.securityScopeId from Workspace workspace where workspace.id = :workspaceId")
    Optional<UUID> findSecurityScopeIdById(UUID workspaceId);
}
