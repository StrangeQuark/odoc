package com.strangequark.odoc.page;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pages/{pageId}/favorite")
public class PageFavoriteController {
    private final PageFavoriteService favorites;
    PageFavoriteController(PageFavoriteService favorites) { this.favorites = favorites; }
    @GetMapping Map<String, Boolean> get(@PathVariable UUID pageId, Principal p) { return Map.of("favorite", favorites.isFavorite(pageId, p.getName())); }
    @PutMapping void put(@PathVariable UUID pageId, Principal p) { favorites.favorite(pageId, p.getName()); }
    @DeleteMapping void delete(@PathVariable UUID pageId, Principal p) { favorites.unfavorite(pageId, p.getName()); }
}
