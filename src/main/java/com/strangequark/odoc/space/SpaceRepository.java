package com.strangequark.odoc.space;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
    List<Space> findAllByWorkspaceIdInOrderByNameAsc(Collection<UUID> workspaceIds);
    Optional<Space> findByWorkspaceIdAndKey(UUID workspaceId, String key);
    boolean existsByIdAndWorkspaceIdIn(UUID spaceId, Collection<UUID> workspaceIds);

    @Query("select space.workspaceId from Space space where space.id = :spaceId")
    Optional<UUID> findWorkspaceIdById(UUID spaceId);
}
