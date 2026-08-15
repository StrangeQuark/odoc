package com.strangequark.odoc.page;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findAllBySpaceIdOrderByUpdatedAtDesc(UUID spaceId);

    boolean existsByContentContaining(String content);

    @Query(value = """
            select * from pages
            where search_document @@ websearch_to_tsquery('english', :query)
            order by ts_rank_cd(search_document, websearch_to_tsquery('english', :query)) desc, updated_at desc
            limit 25
            """, nativeQuery = true)
    List<Page> search(@Param("query") String query);
}
