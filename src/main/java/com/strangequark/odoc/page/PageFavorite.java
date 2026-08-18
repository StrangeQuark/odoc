package com.strangequark.odoc.page;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name = "page_favorites") @IdClass(PageFavorite.Key.class)
class PageFavorite {
    @Id private UUID pageId; @Id private String username; private Instant createdAt;
    protected PageFavorite() { }
    PageFavorite(UUID pageId, String username, Instant createdAt) { this.pageId = pageId; this.username = username; this.createdAt = createdAt; }
    static class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        UUID pageId; String username;
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(pageId, key.pageId) && Objects.equals(username, key.username);
        }
        @Override public int hashCode() { return Objects.hash(pageId, username); }
    }
}
