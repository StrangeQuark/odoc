package com.strangequark.odoc.page;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PageFavoriteRepository extends JpaRepository<PageFavorite, PageFavorite.Key> {
    boolean existsByPageIdAndUsername(UUID pageId, String username);
    void deleteByPageIdAndUsername(UUID pageId, String username);
}
