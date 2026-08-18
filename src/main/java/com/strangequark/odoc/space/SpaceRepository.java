package com.strangequark.odoc.space;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    List<Space> findAllByWorkspaceIdInOrderByNameAsc(Collection<UUID> workspaceIds);
    Optional<Space> findByWorkspaceIdAndKey(UUID workspaceId, String key);
    boolean existsByIdAndWorkspaceIdIn(UUID spaceId, Collection<UUID> workspaceIds);
}
