package com.strangequark.odoc.workspace;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceGroupRepository extends JpaRepository<WorkspaceGroup, UUID> {
    Optional<WorkspaceGroup> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<WorkspaceGroup> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);
}
