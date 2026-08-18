package com.strangequark.odoc.api;

import java.util.List;

/**
 * Stable envelope for future cursor-paginated APIs. A null cursor means the
 * current page is terminal; it is never an offset in disguise.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {
    public CursorPage {
        items = List.copyOf(items);
    }
}
