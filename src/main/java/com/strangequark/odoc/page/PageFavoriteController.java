package com.strangequark.odoc.page;

import com.strangequark.odoc.auth.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pages/{pageId}/favorite")
public class PageFavoriteController {
    private final PageFavoriteService favorites;
    private final CurrentUser currentUser;
    PageFavoriteController(PageFavoriteService favorites, CurrentUser currentUser) {
        this.favorites = favorites;
        this.currentUser = currentUser;
    }
    @GetMapping Map<String, Boolean> get(@PathVariable UUID pageId) {
        return Map.of("favorite", favorites.isFavorite(pageId, currentUser.requireId().toString()));
    }
    @PutMapping void put(@PathVariable UUID pageId) { favorites.favorite(pageId, currentUser.requireId().toString()); }
    @DeleteMapping void delete(@PathVariable UUID pageId) { favorites.unfavorite(pageId, currentUser.requireId().toString()); }
}
