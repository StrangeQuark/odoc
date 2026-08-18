package com.strangequark.odoc.page;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service class PageFavoriteService {
    private final PageFavoriteRepository favorites; private final PageAccessService pages; private final Clock clock;
    PageFavoriteService(PageFavoriteRepository favorites, PageAccessService pages) { this.favorites = favorites; this.pages = pages; this.clock = Clock.systemUTC(); }
    @Transactional(readOnly = true) boolean isFavorite(UUID pageId, String username) { requirePage(pageId); return favorites.existsByPageIdAndUsername(pageId, username); }
    @Transactional void favorite(UUID pageId, String username) { requirePage(pageId); if (!favorites.existsByPageIdAndUsername(pageId, username)) favorites.save(new PageFavorite(pageId, username, clock.instant())); }
    @Transactional void unfavorite(UUID pageId, String username) { requirePage(pageId); favorites.deleteByPageIdAndUsername(pageId, username); }
    private void requirePage(UUID id) { pages.requireAccessiblePage(id); }
}
