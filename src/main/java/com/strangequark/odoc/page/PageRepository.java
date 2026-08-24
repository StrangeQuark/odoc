package com.strangequark.odoc.page;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findAllBySpaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID spaceId);

    List<Page> findTop500BySpaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID spaceId);

    boolean existsByContentContaining(String content);

    @Query(value = """
            select pages.* from pages
            join spaces on spaces.id = pages.space_id
            where spaces.workspace_id in (:workspaceIds)
              and search_document @@ websearch_to_tsquery('english', :query)
              and pages.archived_at is null
            order by ts_rank_cd(search_document, websearch_to_tsquery('english', :query)) desc, pages.updated_at desc
            limit 25
            """, nativeQuery = true)
    List<Page> searchInWorkspaces(@Param("workspaceIds") List<UUID> workspaceIds, @Param("query") String query);
}
